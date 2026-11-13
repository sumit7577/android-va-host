#include "ElfImage.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>

extern "C" {
#include <xz.h>
}

#include <fb/include/fb/ALog.h>

namespace va {

namespace {

struct FindModule {
    std::string_view name;
    void *base;
    std::string path;
    ElfW(Addr) bias;
};

/**
 * dl_iterate_phdr gives the load base and the path of every loaded object,
 * which is how the module is found without hardcoding /apex paths that move
 * between releases.
 */
int onModule(struct dl_phdr_info *info, size_t, void *data) {
    auto *find = static_cast<FindModule *>(data);
    if (info->dlpi_name == nullptr) {
        return 0;
    }
    std::string_view name(info->dlpi_name);
    if (name.find(find->name) == std::string_view::npos) {
        return 0;
    }
    find->base = reinterpret_cast<void *>(info->dlpi_addr);
    find->path = info->dlpi_name;

    // The bias is dlpi_addr for a PIE/shared object, but derive it from the
    // first PT_LOAD so a prelinked object is handled too.
    find->bias = info->dlpi_addr;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) &ph = info->dlpi_phdr[i];
        if (ph.p_type == PT_LOAD) {
            find->bias = info->dlpi_addr + ph.p_vaddr - ph.p_offset;
            break;
        }
    }
    return 1;
}

}  // namespace

ElfImage::ElfImage(std::string_view name) {
    FindModule find{name, nullptr, {}, 0};
    dl_iterate_phdr(onModule, &find);
    if (find.base == nullptr) {
        ALOGE("ElfImage: module %.*s is not loaded", (int) name.size(), name.data());
        return;
    }
    base_ = find.base;
    path_ = find.path;
    bias_ = find.bias;
    parse();
}

ElfImage::~ElfImage() {
    if (image_ != nullptr) {
        munmap(image_, size_);
    }
    free(mini_);
}

void ElfImage::parse() {
    int fd = open(path_.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        ALOGE("ElfImage: cannot open %s", path_.c_str());
        return;
    }
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        return;
    }
    size_ = static_cast<size_t>(st.st_size);
    image_ = mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (image_ == MAP_FAILED) {
        image_ = nullptr;
        ALOGE("ElfImage: cannot map %s", path_.c_str());
        return;
    }

    auto *base = static_cast<char *>(image_);
    header_ = reinterpret_cast<const ElfW(Ehdr) *>(base);
    if (memcmp(header_->e_ident, ELFMAG, SELFMAG) != 0) {
        ALOGE("ElfImage: %s is not an ELF file", path_.c_str());
        header_ = nullptr;
        return;
    }

    auto *sections = reinterpret_cast<const ElfW(Shdr) *>(base + header_->e_shoff);
    if (header_->e_shstrndx >= header_->e_shnum) {
        return;
    }
    const char *shstr = base + sections[header_->e_shstrndx].sh_offset;

    // Both tables are read when present; .symtab is the one a release build
    // usually strips.
    for (int i = 0; i < header_->e_shnum; i++) {
        const ElfW(Shdr) &sh = sections[i];
        const char *sname = shstr + sh.sh_name;
        if (sh.sh_type == SHT_PROGBITS && strcmp(sname, ".gnu_debugdata") == 0) {
            parseMiniDebugInfo(base + sh.sh_offset, sh.sh_size);
            continue;
        }
        if (sh.sh_type != SHT_SYMTAB && sh.sh_type != SHT_DYNSYM) {
            continue;
        }
        if (sh.sh_link >= header_->e_shnum || sh.sh_entsize == 0) {
            continue;
        }
        const ElfW(Shdr) &strtab = sections[sh.sh_link];
        collect(reinterpret_cast<const ElfW(Sym) *>(base + sh.sh_offset),
                sh.sh_size / sh.sh_entsize,
                base + strtab.sh_offset);
    }
    ALOGI("ElfImage: %s -> %zu symbols", path_.c_str(), symbols_.size());
}

void ElfImage::collect(const ElfW(Sym) *table, size_t count, const char *strings) {
    for (size_t i = 0; i < count; i++) {
        const ElfW(Sym) &sym = table[i];
        // SHN_UNDEF has no address here; it is satisfied by another object.
        if (sym.st_value == 0 || sym.st_shndx == SHN_UNDEF || sym.st_name == 0) {
            continue;
        }
        const char *name = strings + sym.st_name;
        if (name[0] == '\0') {
            continue;
        }
        symbols_.emplace(name, sym.st_value);
    }
}

void *ElfImage::symbol(std::string_view name) const {
    if (!valid()) {
        return nullptr;
    }
    auto it = symbols_.find(std::string(name));
    if (it == symbols_.end()) {
        return nullptr;
    }
    return reinterpret_cast<void *>(bias_ + it->second);
}

void *ElfImage::symbolByPrefix(std::string_view prefix) const {
    if (!valid()) {
        return nullptr;
    }
    for (const auto &entry : symbols_) {
        if (entry.first.size() >= prefix.size() &&
            entry.first.compare(0, prefix.size(), prefix) == 0) {
            return reinterpret_cast<void *>(bias_ + entry.second);
        }
    }
    return nullptr;
}


void ElfImage::parseMiniDebugInfo(const void *data, size_t size) {
    // The platform strips .symtab but leaves this XZ-compressed ELF behind, so
    // it is the only place ART's internal symbols survive on a release build.
    xz_crc32_init();
    struct xz_dec *dec = xz_dec_init(XZ_DYNALLOC, 1 << 26);
    if (dec == nullptr) {
        ALOGE("ElfImage: xz_dec_init failed");
        return;
    }

    // Size is not recorded in the section, so grow until the stream ends.
    size_t capacity = size * 8 + (1 << 20);
    auto *out = static_cast<uint8_t *>(malloc(capacity));
    if (out == nullptr) {
        xz_dec_end(dec);
        return;
    }

    struct xz_buf buf{};
    buf.in = static_cast<const uint8_t *>(data);
    buf.in_pos = 0;
    buf.in_size = size;
    buf.out = out;
    buf.out_pos = 0;
    buf.out_size = capacity;

    enum xz_ret ret;
    while (true) {
        ret = xz_dec_run(dec, &buf);
        if (ret != XZ_OK) {
            break;
        }
        if (buf.out_pos < buf.out_size) {
            // XZ_OK with room left means it wants more input, and there is
            // none: the stream is truncated.
            break;
        }
        capacity *= 2;
        auto *grown = static_cast<uint8_t *>(realloc(out, capacity));
        if (grown == nullptr) {
            break;
        }
        out = grown;
        buf.out = out;
        buf.out_size = capacity;
    }
    xz_dec_end(dec);

    if (ret != XZ_STREAM_END) {
        ALOGE("ElfImage: .gnu_debugdata decode failed (xz_ret=%d)", ret);
        free(out);
        return;
    }

    mini_ = out;
    miniSize_ = buf.out_pos;

    auto *base = reinterpret_cast<char *>(mini_);
    auto *header = reinterpret_cast<const ElfW(Ehdr) *>(base);
    if (miniSize_ < sizeof(ElfW(Ehdr)) || memcmp(header->e_ident, ELFMAG, SELFMAG) != 0) {
        ALOGE("ElfImage: .gnu_debugdata is not an ELF image");
        return;
    }
    auto *sections = reinterpret_cast<const ElfW(Shdr) *>(base + header->e_shoff);
    size_t before = symbols_.size();
    for (int i = 0; i < header->e_shnum; i++) {
        const ElfW(Shdr) &sh = sections[i];
        if (sh.sh_type != SHT_SYMTAB || sh.sh_entsize == 0 || sh.sh_link >= header->e_shnum) {
            continue;
        }
        const ElfW(Shdr) &strtab = sections[sh.sh_link];
        collect(reinterpret_cast<const ElfW(Sym) *>(base + sh.sh_offset),
                sh.sh_size / sh.sh_entsize,
                base + strtab.sh_offset);
    }
    ALOGI("ElfImage: .gnu_debugdata added %zu symbols", symbols_.size() - before);
}

}  // namespace va
