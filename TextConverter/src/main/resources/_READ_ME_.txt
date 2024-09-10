#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
#!
#! To save you having to dig around for the information below, if there is
#! more than one non-forced definition for a given item, the definition which
#! takes precedence is the one which is encountered
#!
#!     _                    _____   _______
#!    | |          /\      / ____| |__   __|
#!    | |         /  \    | (___      | |
#!    | |        / /\ \    \___ \     | |
#!    | |____   / ____ \   ____) |    | |
#!    |______| /_/    \_\ |_____/     |_|
#!
#!
#! unless any earlier definitions have been marked as 'force'.  With things marked
#! 'force', the _first_ definition is used.
#!
#! This will make more sense once you have read the rest of the documentation.
#!
#!
#!
#!
#!
#! Configuration data -- an overview
#! =================================
#!
#! If you want to cut to the chase (and particularly if you already know roughly
#! how the configuration information works), there is a section entitled 'How to
#! set up configuration information' later in these notes.  Anyway, on with the
#! discussion ...
#!
#! There's a _lot_ of configuration information.  Having said which, that's
#! mainly because we never quite know in advance what we may need to configure:
#! new texts often bring new surprises.
#!
#! The good news, though, is that the amount of configuring you _have_ to do
#! for any individual text is usually quite modest, and in the main covers the
#! sorts of things you would _expect_ to have to supply, like copyright
#! information.  And in fact if you have a number of texts from the same source,
#! you can often reduce this further by sharing information between them.
#!
#! Configuration information comes from one of four places ...
#!
#! - You can place settings into an environment variable --
#!   StepTextConverterParameters.  This  is handy for settings which apply to
#!   _every_ module, and which you need to be in place at the very start of
#!   processing.
#!
#! - You can supply settings on the command line.  You use this for certain
#!   control information specific to the text you are working on (like the
#!   name of the folder where the data files for the text reside).
#!
#! - There is a lot of configuration data built into the Resources section of
#!   the current JAR file.  This is standard stuff which is probably going to
#!   be correct for every module, but which you can override where necessary.
#!   You probably need to consider very carefully before changing anything here
#!   or overriding any of the definitions.
#!
#! - And you can set up and store configuration files of your own.  This is
#!   where you store material specific to a given module (or shared between
#!   a collection of modules) which is unlikely to change all that often.
#!
#!
#!
#! Having said that you will not need to refer to this Resources section, you
#! may, in fact, need to read through the files there to understand what they
#! do, and therefore what things you might usefully override.
#!
#! Most of the configuration files in the Resources section work in the same
#! way (hopefully _exactly_ the same way), but each sets out to do different
#! things, and will therefore contain different kinds of data.  Each file
#! gives more details of what it sets out to achieve and what data it defines
#! in its own head-of-file comments, and I won't bother duplicating that
#! information here.  Instead I restrict myself here to detailing the common
#! syntax rules and mechanics.
#!
#! (I say 'most' of the configuration files work the same way because there
#! are a few tab-separated variable files and / or text files which contain
#! fixed information, and which don't need to participate in the overall
#! scheme for configuration data.  All 'genuine' configuration files here
#! have an extension of '.conf'.)
#!
#! Key to all the config data is a mechanism which lets you include one file
#! from another, along with rules which dictate how duplicate definitions for
#! the same parameter are handled.  These are covered in the next two sections,
#! before we look in more detail at the mechanics.
#!
#!
#!
#!
#!
#! Kicking things off
#! ==================
#!
#! All texts have to have a Metadata folder, and that folder must contain a
#! file called step.conf.  (It may contain other things too, but it must have
#! step.conf.)
#!
#! You put into step.conf (or into files included from step.conf) the
#! definitions of any configuration information relevant to that particular
#! text.
#!
#! You also include from step.conf a file 'commonRoot.conf' which arranges for
#! default values for any parameters to which you have not assigned a specific
#! value.
#!
#! If you run the converter with the parameter '-dbgConfigData generateStepConfig[All]',
#! it will output to stdout an outline version of step.conf.  You can paste this
#! into a file and then alter it as necessary.  Giving the parameter as
#! generateStepConfig limits the output to the configuration parameters you are
#! most likely to want to change (although even then you probably won't change
#! _all_ of them).  Giving the parameter as generateStepConfigAll gives details
#! of additional parameters which it may occasionally be useful to know about.
#!
#!
#!
#!
#!
#! The $include mechanism
#! ======================
#!
#! The $include mechanism lets you include one file from another.  $include
#! statements come in two forms:
#!
#!   $include <path>
#!   $includeIfExists <path>
#!
#!
#! $include attempts to include the file, and aborts the run if it cannot find
#! it.  $includeIfExists includes the file if it exists, but does not worry if
#! it does not.
#!
#! The processing replaces each $include statement by the content of the file
#! to which it points.  It then looks over the resulting data and replaces any
#! $include statements which now appear ... and so on.
#!
#! The '<path>' above represents the path by which the file can be located.
#! The path has to be in Linux format -- ie separators are '/', and you can use
#! '.' and '..' if you wish (with some limitations).  The main remaining
#! issue is how to start the path.
#!
#! A path starting $jarResources/ refers to files in the resources section of this
#! JAR.  So $jarResources/ReferenceFormats/referenceFormatVernacularDefaults.conf
#! points to a file of the given name in the ReferenceFormats subfolder of the
#! Resources section.
#!
#! A path starting $find/ examines a predefined list of locations in a predefined
#! order: the root folder for the text, the metadata folder for the text, and
#! the shared configuration folder (_SharedConfig_).
#!
#! Or you can give an absolute path.  I recommend against this, however, because
#! it will make your configuration data less portable.
#!
#! $include statements are subject to substitution in the normal way (which we
#! discuss at exhausting length shortly).  Thus you can have something like:
#!
#!  @(stepMyPredefinedFolder)/myfile.conf
#!
#! and the processing will substitute for @(stepMyPredefinedFolder) whatever
#! value you have associated with stepMyPredefinedFolder, and then work with
#! that expanded path.
#!
#!
#!
#! The tool ascribes no significance to the manner in which configuration data
#! is split between files, so you can split things up as you like.  Obviously it
#! may be useful to group associated data into the same file.  In particular, if
#! you have data which could usefully be shared between a number of texts
#! (for example details of a common copyright holder), you may want to put that
#! information in a separate file and store it somewhere under the shared config
#! folder.
#!
#! My strong recommendation is that you do not avail yourself of all this
#! flexibility. If you want your own configuration files, then ...
#!
#! - If they are specific to an individual text, put them in the Metadata folder
#!   for that text.
#!
#! - If they are going to be shared by multiple texts, put them in the shared
#!   configuration folder.
#!
#!
#! Files from these two areas are automatically included in the repository
#! package which the converter generates, and therefore will be available
#! should we need to rebuild the module.
#!
#!
#!
#!
#!
#! Multiple definitions
#! ====================
#!
#! The use of multiple files as described above does rather invite multiple
#! definitions of the same parameter.  Indeed it would not be possible to
#! arrange to have overridable defaults without it.
#!
#! A parameter defined on the command line always takes precedence over anything
#! else, so that any further definitions for the same parameter are ignored.
#!
#! After this, it is as though any definitions in the environment variable
#! StepTextConverterParameters appeared in a config file of their own, and
#! this pseudo file were expanded out before anything else.  Then after
#! processing this pseudo file, all of the other configuration files are
#! expanded out as described above.
#!
#! The resulting expanded text is processed in order from start to end, and the
#!
#!     _                    _____   _______
#!    | |          /\      / ____| |__   __|
#!    | |         /  \    | (___      | |
#!    | |        / /\ \    \___ \     | |
#!    | |____   / ____ \   ____) |    | |
#!    |______| /_/    \_\ |_____/     |_|
#!
#!
#! definition one to be encountered takes precedence unless you indicate otherwise.
#! (How you _do_ indicate otherwise is discussed shortly.)
#!
#! (There are a few special exceptions to this: some parameters can take
#! multiple values, and where this is the case, typically you give multiple
#! definitions for the same parameter, and rather than the final one overriding
#! the others, all of them are used.)
#!
#!
#!
#!
#!
#! General syntax
#! ==============
#!
#! Comments are marked with #!.  The #! may occur anywhere on a line: you may
#! use it to comment out an entire line, or you may use it to add some
#! explanatory text on to the end of some other statement.
#!
#! Blank lines and pure comment lines are ignored.
#!
#! You may split a statement across multiple lines -- simply end the all but the
#! last line with a backslash.
#!
#! Internally, all parameters have names starting with 'step'.  (Parameters on
#! the command line do not have this prefix, but they are converted so that,
#! for example, the command line parameter rootFolder becomes stepRootFolder
#! when referred to internally.)
#!
#! The most common type of a statement is a definition, which, in its
#! simplest form, looks like:
#!
#!   stepSong=Jingle bells
#!
#! This associates the value 'Jingle bells' with the configuration parameter
#! stepSong, so that any time the former is referenced, it will be replaced
#! by this value.  Parameter names are case-sensitive.
#!
#! Leading and trailing spaces in the value are ignored.  If you wish to include
#! a space there, give it as '{space}'.
#!
#! Definitions which lack a right-hand side
#!
#!   stepMyThing=
#!
#! associate an empty string with the key.  For the sake of clarity, you
#! can also give the right-hand side as '{empty}' to achieve the same effect,
#! and I recommend you do so.
#!
#! This is the 'standard' form of definition.  If more than one definition like
#! this for stepSong exists, only the latest one to be encountered is
#! actually used.  So
#!
#!   stepSong=Jingle bells
#!   stepSong=Happy birthday
#!
#! associates 'Happy birthday' with stepSong.
#!
#! There is a variant of the above, which looks like:
#!
#!   stepSong#=Jingle bells
#!
#! (note the #).  This represents a _forcible_ definition.  Things work as
#! follows:
#!
#! - If you have multiple non-forced ('=') definitions, the _last_ one to be
#!   encountered takes precedence.
#!
#! - If you have a single forced definition, that takes precedence over any
#!   non-forced assignments, regardless of whether they are encountered before
#!   or after the forced definition.
#!
#! - If you have more than one forced definition, the _first_ takes precedence.
#!
#!
#! You should always end your step.conf file by including commonRoot.conf (the
#! sample step.conf generated by the converter does this for you).  I
#! recommend inserting all of your own parameter definitions _before_ that
#! $include statement, and making them all forcible definitions.
#!
#!
#!
#!
#!
#! Dependent settings
#! ==================
#!
#! Sometimes it's useful to be able to take values which you have defined
#! previously and use them as part of the definition of a new parameter:
#!
#!   stepYourName=Ethelred
#!   stepWelcome=Welcome, @(stepYourName)
#!
#! The @(...) is replaced by the value of the parameter which it names.  In the
#! form shown here, it is an error if stepYourName is undefined.  There are
#! related alternatives, though:
#!
#!   stepWelcome=Welcome, @(stepYourName, stepMyName, ..., =friend)
#!
#! In this case, the processing runs through the list of parameter names from
#! the left until it finds one which is defined, in which case it uses that.  If
#! none is defined, it uses the default value (which must be prefixed with '=').
#! So if stepYourName and stepMyName etc are all undefined, stepWelcome becomes
#! 'Welcome, friend'.  The default value (which is treated as literal text) is
#! optional.  It is again an error if the processing needs to fall back upon a
#! default and none has been defined.
#!
#! A further subtle variant is @choose:
#!
#!   stepWelcome=Welcome, @choose(@(stepMyName), @(stepYourName), =friend)
#!
#! Where @(...) finds the first acceptable value, and then expands @-references
#! in that if there are any, and so on until there are no more @-references ...
#! where @(...) does that, @choose(...) simply finds the first acceptable value
#! and then does no further expansion.
#!
#! You may also use @getExternal to obtain data from an external metadata file
#! (currently available only with DBL files).  This is discussed in more detail
#! in PerTextRepositoryOrganisation/Dbl.conf.
#!
#! These various @-things can be nested in any way you like to any depth -- eg
#!
#!   @choose( @(myImportantParameter), @getExternal(metadata, DBLMetadata/thing), =DEFAULT VALUE)
#!
#! I wouldn't advise doing much of this nesting, though -- debugging complex
#! configuration information can be difficult.
#!
#!
#!
#!
#!
#! Vernacular text fragments
#! =========================
#!
#! The conversion process makes use of certain standard pieces of text, for
#! example in reversification footnotes.  There is a file
#! vernacularTranslationsDb.txt which contains translations of all of these.
#! I keep this in my shared configuration data folder, and I recommend you
#! do the same.
#!
#! The file contains more comments about its structure and how you can set it
#! up or alter it.
#!
#!
#!
#!
#!
#! History lines
#! =============
#!
#! The Sword configuration file is supposed to include History lines which
#! give the change history for a module.  The processing itself manages these
#! lines, and updates the step.conf file at the end of each run, so that the
#! history lines used most recently are available for use in future runs.
#!
#! You can seed step.conf with an initial collection of history lines (so long
#! as you use the correct format), although there is no _need_ for you to do so;
#! and you can also modify the history rows which appear in the file at any time
#! so that the revised lines will be used as input next time round.
#!
#! A couple of caveats apply, however:
#!
#! - You probably don't want to use the @(...) mechanism to incorporate
#!   looked-up values into history lines at run time.  This _will_ work, but
#!   when I write the history lines back into the file, I will write the
#!   _expanded_ version, and so the @(...) will not be available for future
#!   runs.
#!
#! - If you do opt to put history lines into your initial configuration
#!   information, put them into step.conf, not into some file included from
#!   it.  When I write the history lines back, they will end up in step.conf,
#!   and things will get confusing if you also had history lines in some other
#!   file.
#!
#! - When you do a new run, you supply details on the command line of why the
#!   run is being carried out -- because the supplier has given us a new
#!   version or for some reason of our own.  The module is then automatically
#!   given a new version number and a new history line.  EXCEPT ... if the
#!   reason details have not changed, I assume we're simply having another go
#!   at a module which was wrong for some reason, and don't up-issue or alter
#!   the history information.  If you want to have the module up-issued
#!   regardless, you can give the setting '-forceUpIssue yes' on the command-
#!   line.
#!
#!
#!
#!
#!
#! Miscellaneous
#! =============
#!
#! * There are some calculated values which can be accessed as though they
#!   These can be a little difficult to use, because they become available only
#!   after the processing which generates them has run, so you have to know when
#!   it is safe to attempt to access them.
#!
#! * Reference was made above to the StepTextConverterParameters environment
#!   variable.  This is probably best limited just to simple parameter settings
#!   (a=b or a#=b), with no @-processing.  It must contain at least the
#!   following settings:
#!
#!     stepTextConverterOverallDataRoot=somePath; stepStepOsis2modFilePath=somePath; stepCrosswireOsis2modFilePath=someOtherPath
#!
#!   which give the path to the folder under which all of your texts fall
#!   (directly or indirectly), and then, respectively where to find the
#!   Crosswire and STEP versions of osis2mod respectively.
#!
#!   If you do this, then when you supply the command line parameter rootFolder,
#!   the processing will assume this as the start of the path, and you need only
#!   add the remainder.  No particular benefit as far as processing is concerned,
#!   but it will save a little typing, perhaps.
#!
#!   You can add other settings too if you wish.  It will be apparent from above
#!   that settings must be separated by a semicolon (which can optionally be
#!   preceded or followed by spaces).  If a setting needs to include a semicolon,
#!   give it as \;.  If you need a backslash, give it as \\.
#!
#!
#!
#!
#!
#! Obtaining data from external sources
#! ====================================
#!
#! Setting up configuration information can be quite an onerous undertaking,
#! particularly if you are having to deal with many texts at the same time.
#!
#! Where texts are coming from a common source, they may well come with
#! standardised metadata (this is the case with DBL, for instance), and it
#! may be useful to be able to transfer some of the information direct from this
#! metadata, rather than have to transcribe it manually.
#!
#! At the time of writing, DBL is, in fact, the only source which supplies
#! standardised metadata, and we do indeed have facilities to extract data
#! from their metadata.xml.  See Dbl.conf for details, and also to get an idea
#! of what may be involved if you wish to set up something similar for some
#! other data source in future.
#!
#!
#!
#!
#!
#! Default settings -- the files in this Resources section
#! =======================================================
#!
#! - basicDefinitions.conf: Things like standard Creative Commons copyright
#!   wording, standard thanks etc.
#!
#! - commonRoot.conf: Mainly arranges for the files containing the various
#!   defaults to be included.  Directly or indirectly your step.conf always
#!   needs to include this.  (The standard template for step.conf _does_ include
#!   it, so in the normal course of events you don't need to worry about it.)
#!
#! - externalDataPaths.conf: Mainly URLs to data which the processing needs to
#!   pick up from the internet, or standard URLs which it may be useful to
#!   include in copyright information.
#!
#! - PerTextRepositoryOrganisation: Common information about text repositories.
#!   Currently limited to DBL.
#!
#! - ReferenceFormats: Describes how references are formatted.  Contains just
#!   one file (which actually defines the format for USX).  You probably
#!   shouldn't change this particular file, but you can use it as a basis for
#!   working out how to define overrides tailored to the text you are handling.
#!
#! - standardBookNames.conf: Lists USX book names in abbreviated, short and
#!   long form.  You can use this as a model for your own list if you need
#!   to support vernacular forms.  (If you have a DBL metadata file, the
#!   necessary information can often be picked up from that file automatically.)
#!
#! - swordTemplateConfigFile.conf: Used by the processing to determine what a
#!   Sword configuration file should look like.  In the normal course of events,
#!   you should never need to change this.
#!
#! - usxToOsisTagConversionsEtc.conf: This defines how _most_ USX tags are
#!   converted to OSIS.  (Most, but not all -- there are some where the
#!   translation is too complicated to be described in a configuration file.)
#!   You shouldn't need to change this often, but you may need to override
#!   settings for a particular file, or where you discover that osis2mod isn't
#!   handling the generated OSIS correctly.  Note also that at the time of
#!   writing, I don't have translations for all possible USX tags -- only for
#!   the ones we have encountered to date.
#!
#!
#!
#! And there are a few files in the resources section which don't function as
#! configuration files, but which fulfil a vaguely similar role:
#!
#! - configDataDescriptors.tsv: Descriptions of all of the configuration
#!   information.
#!
#! - countryNamesToShortenedForm.tsv: Details of countries whose names might
#!   reasonably be converted to a rather shorter form.
#!
#! - isoLanguageCodes.tsv: Details of language codes.
#!
#! - osis2modVersification.txt: Details of the versification schemes supported
#!   by Crosswire's version of osis2mod.
#!
#! - strongsCorrections.txt: Replacement values for Strong's code which are
#!   are often incorrect when provided to us.
#!
#!
#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
