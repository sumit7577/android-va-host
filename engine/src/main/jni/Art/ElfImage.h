#ifndef VA_ELF_IMAGE_H
#define VA_ELF_IMAGE_H

#include <elf.h>
#include <link.h>

#include <string>
#include <string_view>
#include <unordered_map>

namespace va {

/**
 * Minimal ELF symbol reader, written for LSPlant's art_symbol_resolver.
 *
 * LSPlant resolves ART internals by name and ships no parser of its own, and
 * dlsym() is not enough: the symbols it wants are not exported from
 * libart.so. So the file is read from disk for its symbol tables while the
 * load base comes from the loaded module, and a symbol's runtime address is
 * base + st_value.
 *
 * Both tables are read. .dynsym is always present; .symtab usually is not on a
 * release build, which is the whole reason symbol-based ART hooking is
 * fragile on recent Android -- if a needed symbol lives only in .symtab and
 * the platform stripped it, nothing here can recover it.
 */
class ElfImage {
public:
    explicit ElfImage(std::string_view name);

    ~ElfImage();

    bool valid() const { return base_ != nullptr && header_ != nullptr; }

    /** Runtime address of an exact symbol name, or nullptr. */
    void *symbol(std::string_view name) const;

    /** Runtime address of the first symbol starting with the prefix, or nullptr. */
    void *symbolByPrefix(std::string_view prefix) const;

    size_t symbolCount() const { return symbols_.size(); }

    const std::string &path() const { return path_; }

private:
    void parse();
    void collect(const ElfW(Sym) * table, size_t count, const char *strings);

    /**
     * Reads .gnu_debugdata (MiniDebugInfo): an XZ-compressed ELF holding the
     * .symtab the platform stripped out. On Android 15 this is where ART's
     * internal symbols actually live -- .dynsym has ~2.7k, this has ~16.7k,
     * and the ones LSPlant needs are only in here.
     */
    void parseMiniDebugInfo(const void *data, size_t size);

    /** Decompressed MiniDebugInfo, kept alive because symbol names point into it. */
    void *mini_ = nullptr;
    size_t miniSize_ = 0;

    /** Where the module is loaded; addresses are relative to this. */
    void *base_ = nullptr;
    std::string path_;

    /** The on-disk image, mmapped for its symbol tables. */
    void *image_ = nullptr;
    size_t size_ = 0;
    const ElfW(Ehdr) *header_ = nullptr;

    /** Offset between the file's virtual addresses and the load base. */
    ElfW(Addr) bias_ = 0;

    std::unordered_map<std::string, ElfW(Addr)> symbols_;
};

}  // namespace va

#endif  // VA_ELF_IMAGE_H
