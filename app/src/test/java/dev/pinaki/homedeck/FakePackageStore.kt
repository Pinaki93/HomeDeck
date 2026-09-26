package dev.pinaki.homedeck

internal class FakePackageStore(
    var result: Result<List<LauncherPackage>> = Result.success(emptyList()),
) : PackageStore {
    val requests = mutableListOf<Shortcut>()

    override fun load(shortcut: Shortcut): Result<List<LauncherPackage>> {
        requests += shortcut
        return result
    }
}
