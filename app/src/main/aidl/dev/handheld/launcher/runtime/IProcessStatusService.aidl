package dev.handheld.launcher.runtime;

// Read-only, bounded process presence. No shell command or file path crosses this API.
interface IProcessStatusService {
    String[] runningPackages(in String[] packages, int userId) = 0;
    void destroy() = 16777114;
}
