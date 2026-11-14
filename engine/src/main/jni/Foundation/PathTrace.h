#ifndef VA_PATH_TRACE_H
#define VA_PATH_TRACE_H

namespace va {

/**
 * Reports the file paths a guest's own native code asks for and does not get.
 *
 * On arm64 this container does no syscall-level path redirection (see the note
 * at the top of IOUniformer.cpp), so a guest's native code sees the real
 * filesystem: its own package is not under /data/app or /data/data, and any
 * library that derives its paths instead of reading the ApplicationInfo it was
 * handed looks at a path that does not exist. That is invisible from Java --
 * the loader just declines to do its work -- so this names the paths.
 *
 * Diagnostic only: it redirects nothing. Returns false if the hooks could not
 * be installed, which changes nothing about how the guest runs.
 */
bool StartPathTrace();

}  // namespace va

#endif  // VA_PATH_TRACE_H
