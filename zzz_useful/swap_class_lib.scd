
( // enable repo class lib
LanguageConfig.addExcludePath(Platform.resourceDir +/+ "SCClassLibrary");
LanguageConfig.addIncludePath("/Users/dyne/src/supercollider/SCClassLibrary");
LanguageConfig.store;
thisProcess.recompile
)


( // restore default class lib
LanguageConfig.removeExcludePath(Platform.resourceDir +/+ "SCClassLibrary");
LanguageConfig.removeIncludePath("/Users/dyne/src/supercollider/SCClassLibrary");
LanguageConfig.store;
thisProcess.recompile;
)

Platform.classLibraryDir // current compiled
LanguageConfig.excludePaths.do(_.postln);
LanguageConfig.includePaths.do(_.postln);
