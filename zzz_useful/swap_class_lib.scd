
( // enable repo class lib
LanguageConfig.addExcludePath(Platform.resourceDir +/+ "SCClassLibrary");
LanguageConfig.addIncludePath("/Users/admin/src/supercollider-mtmccrea/SCClassLibrary");
LanguageConfig.store;
thisProcess.recompile
)


( // restore defaul class lib
LanguageConfig.removeExcludePath(Platform.resourceDir +/+ "SCClassLibrary");
LanguageConfig.removeIncludePath("/Users/admin/src/supercollider-mtmccrea/SCClassLibrary");
LanguageConfig.store;
thisProcess.recompile;
)

Platform.classLibraryDir // current compiled
LanguageConfig.excludePaths.postlns;
LanguageConfig.includePaths.postlns;
