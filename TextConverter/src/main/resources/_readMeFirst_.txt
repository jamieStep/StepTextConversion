#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
#!
#! To save you having to dig around for the information below, if there is
#! more than one definition for a given item, the definition which takes
#! precedence is the one which is encountered
#!
#!  .----------------.  .----------------.  .----------------.  .----------------. 
#! | .--------------. || .--------------. || .--------------. || .--------------. |
#! | |   _____      | || |      __      | || |    _______   | || |  _________   | |
#! | |  |_   _|     | || |     /  \     | || |   /  ___  |  | || | |  _   _  |  | |
#! | |    | |       | || |    / /\ \    | || |  |  (__ \_|  | || | |_/ | | \_|  | |
#! | |    | |   _   | || |   / ____ \   | || |   '.___`-.   | || |     | |      | |
#! | |   _| |__/ |  | || | _/ /    \ \_ | || |  |`\____) |  | || |    _| |_     | |
#! | |  |________|  | || ||____|  |____|| || |  |_______.'  | || |   |_____|    | |
#! | |              | || |              | || |              | || |              | |
#! | '--------------' || '--------------' || '--------------' || '--------------' |
#!  '----------------'  '----------------'  '----------------'  '----------------' 
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
#! The good news, though, is that that amount of configuring you _have_ to do
#! for any individual text is usually quite modest, and in the main covers the
#! sorts of things you would _expect_ to have to supply, like copyright
#! information.  And in fact if you have a number of texts from the same source,
#! you can often reduce this further by sharing information between them.
#!
#! Configuration information comes from one of four places.  You can place
#! settings into an environment variable -- StepTextConverterParameters.
#! A few parameters you supply on the command line when you run the converter.
#! Then there are a lot of default settings built into the Resources section of
#! the present JAR file.  And finally, you supply your own settings in
#! configuration files you construct yourself.
#!
#! Each of the configuration files in this Resources section work in the same
#! way (hopefully _exactly_ the same way), but each sets out to do different
#! things, and will therefore contain different kinds of data.  Each file
#! gives more details of what it sets out to achieve and what data it defines
#! in its own head-of-file comments, and I won't bother duplicating that
#! information here.  Instead I restrict myself here to detailing the common
#! syntax rules and mechanics.
#!
#! Key to all of this is a mechanism which lets you include one file from
#! another, along with rules which dictate how duplicate definitions for the
#! same parameter are handled.  These are covered in the next two sections,
#! before we look in more detail at the mechanics.
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
#! A path starting $common/ refers to files in the resources section of this
#! JAR.  Thus $common/ReferenceFormats/referenceFormatVernacularDefaults.conf
#! points to a file of the given name in the ReferenceFormats subfolder of the
#! Resources section.  This is the only case where use of '..' is restricted:
#! you can't use '..' to take you up above where $common points (because there
#! _is_ nothing above it).
#!
#! With the other options, there is no such limitation.  The other options are:
#!
#! $root/      Points to the root folder for the text (the folder you specify on
#!             the command line when running the converter, via the rootFolder
#!             parameter.
#!
#! $metadata/  Points to the Metadata folder for the text being processed.
#!
#! Other       If this is an absolute path (eg C:/MyFolder/myConf.conf) then
#!             that path is used.  If it is a relative path
#!             (eg YourFolder/yourConf.conf), it is taken as being relative
#!             to the file currently being processed.  So if you are processing
#!             C:/MyFolder/myConf.conf when you encounter the above include, the
#!             file to be processed is C:/MyFolder/YourFolder/yourConf.conf.
#!
#!
#! The tool ascribes no significance to the manner in which configuration data
#! is split between files, so you can split things up as you like.  Obviously it
#! may be useful to group associated data into the same file.  In particular, if
#! you have data which could usefully be shared between a number of texts
#! (for example details of a common copyright holder), you may want to put that
#! information in a separate file and store it somewhere where it can be found
#! via a relative path from each text.
#!
#! Thus, for example, we have a number of texts from Biblica, all of which I
#! have stored under a common folder called Biblica on my computer :-
#!
#!   Biblica
#!   |
#!   +--- Text_deu_HFA
#!   |
#!   +--- Text_Eng_NIV
#!   |
#!   +--- etc
#!
#!
#! All of these texts need access to information about Biblica as an organisation,
#! so it has been convenient to augment the above structure with a folder which I
#! have chosen to call _Metadata_ (the name doesn't matter to the processing), in
#! which I have stored a common Biblica .conf files:
#!
#!   Biblica
#!   |
#!   +--- _Metadata_
#!   |    |
#!   |    +--- Common Biblica .conf files
#!   |
#!   +--- Text_deu_HFA
#!   |
#!   +--- Text_Eng_NIV
#!   |
#!   +--- etc
#!
#!
#! This file can then be reached from each text using a $include statement of
#! the form:
#!
#!  $include $root/../_Metadata_/biblica.conf
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
#! StepTextConverterParameters were expanded out as text, and this pseudo text
#! were then followed by the result of expanding the configuration files as
#! described above (ie of processing the $include statements).
#!
#! The resulting text is processed in order from start to end, and the
#!
#!  .----------------.  .----------------.  .----------------.  .----------------.
#! | .--------------. || .--------------. || .--------------. || .--------------. |
#! | |   _____      | || |      __      | || |    _______   | || |  _________   | |
#! | |  |_   _|     | || |     /  \     | || |   /  ___  |  | || | |  _   _  |  | |
#! | |    | |       | || |    / /\ \    | || |  |  (__ \_|  | || | |_/ | | \_|  | |
#! | |    | |   _   | || |   / ____ \   | || |   '.___`-.   | || |     | |      | |
#! | |   _| |__/ |  | || | _/ /    \ \_ | || |  |`\____) |  | || |    _| |_     | |
#! | |  |________|  | || ||____|  |____|| || |  |_______.'  | || |   |_____|    | |
#! | |              | || |              | || |              | || |              | |
#! | '--------------' || '--------------' || '--------------' || '--------------' |
#!  '----------------'  '----------------'  '----------------'  '----------------'
#!
#! definition one to be encountered takes precedence unless you indicate otherwise.
#! (How you do this is discussed shortly.)
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
#! Internally, all parameters have names starting with 'step'.  (Parameters on
#! the command line to not have this prefix, but they are converted so that,
#! for example, the command line parameter rootFolder becomes stepRootFolder
#! when referred to internally.)
#!
#! Comments are marked with #!.  The #! may occur anywhere on a line: you may
#! use it to comment out an entire line, or you may use it to add some
#! explanatory text on to the end of some other statement.
#!
#! Blank lines and pure comment lines are ignored.
#!
#! You may split a statement across multiple lines -- simply end the earlier
#! lines with a backslash.
#!
#!
#!
#! The most common type of a statement is a definition, which, in its
#! simplest form, looks like:
#!
#!   stepThingie=Jingle bells
#!
#! This associates the value 'Jingle bells' with the configuration parameter
#! stepThingie, so that any time the former is referenced, it will be replaced
#! by this value.  (Parameter names are case-sensitive.)
#!
#! Leading and trailing spaces in the value are ignored.  If you wish to include
#! a space there, give it as {space}.
#!
#! Definitions which lack a right-hand side (stepMyThing=   or stepMyThing#=   )
#! associate an empty string with the key.  For the sake of clarity, you
#! can also give the right-hand side as {empty} to achieve the same effect.
#!
#! This is the 'standard' form of definition.  If more than one definition like
#! this for stepThingie exists, only the latest one to be encountered is
#! actually used.
#!
#! There is a variant of the above, which looks like:
#!
#!   stepThingie#=Jingle bells
#!
#! (note the #).  This gives this definition priority over any others
#! encountered later.  To be specific, this definition will take priority over
#! any later definitions whatsoever, regardless of whether or not these later
#! definitions are of the form x=y or x#=y.
#!
#!
#!
#!
#!
#! Dependent settings
#! ==================
#!
#! Sometimes it's useful to be able to include values defined elsewhere as part
#! of a definition.  You can achieve this as follows:
#!
#!   stepYourName=Ethelred
#!   stepWelcome=Welcome, @(stepYourName)
#!
#! The @(...) is replaced by the value of the parameter which it names.  In the
#! form shown here, it is an error if stepYourName is undefined.  There are
#! related alternatives, though:
#!
#!   stepWelcome=Welcome, @(stepMyName, stepYourName, ..., =friend)
#!
#! In this case, the processing runs through the list of parameter names from
#! the left until it finds one which is defined, in which case it uses that.  If
#! none is defined, it uses the default value (which must be prefixed with '=').
#! So if stepMyName and stepYourName etc are all undefined, stepWelcome becomes
#! 'Welcome, friend'.  The default value (which is treated as literal text) is
#! optional.  It is again an error if the processing needs to fall back upon a
#! default and none has been defined.
#!
#! A further subtle variant is @choose:
#!
#!   stepWelcome=Welcome, @choose(@(stepMyName), @(stepYourName), =friend)
#!
#! In fact this is so subtle as to be difficult to explain (and indeed I'm no
#! longer sure I can really remember what it means anyway), but I _think_
#! @() takes each argument in turn until it finds one that is defined; and then
#! it looks as the associated value to see if that itself needs to be expanded
#! out, and so on.  @choose by contrast merely takes the initial value -- it
#! does not then expand things out further.
#!
#! You may also use @getExternal to obtain data from an external metadata file
#! (currently available only with DBL files).  This is discussed in more detail
#! in ReferenceFormats/Dbl.conf.
#!
#! These various @-things can be nested in any way you like to any depth -- eg
#!
#!   @choose( @(myImportantParameter), @getExternal(metadata, DBLMetadata/thing), =DEFAULT VALUE)
#!
#! I wouldn't advise doing much of this, though -- debugging complex
#! configuration information can be difficult.
#!
#!
#!
#!
#! How to set up configuration information
#! =======================================
#!
#! Every text has to have, in its Metadata folder, a file called step.config
#! which acts as the root for all the configuration information.  If you're
#! working with a text from DBL, you probably also want to store DBL's
#! metadata.xml and license.xml files there too.
#!
#! Far and away the easiest way to do set up your step.config file is to look
#! around for an existing step.config -- particularly if your text is one of
#! a collection having something in common (like all originating from Biblica.)
#!
#! Otherwise, take a copy of step.conf from this present Resources section,
#! and follow the instructions there.  (If you use an existing step.conf as
#! your template instead, you may well find that it lacks a lot of the
#! parameters mentioned in the step.conf here.  This will be either that it
#! has accepted the defaults for those settings, or because a lot of the
#! settings have been moved out elsewhere -- perhaps to a common file shared
#! with a number of other texts.)
#!
#! You will find configuration parameters fall into a number of different
#! categories:
#!
#! - A very few reflect your computing environment, and tell the processing
#!   things like where it can find external executables and so on.  You
#!   may want to store these in a common file which _all_ texts can access.
#!   That way if you need to change things, there's only one place to change.
#!   If you use this shared file approach, you will need to set this up only
#!   once.
#!
#! - Some reflect the specific text (description, copyright owner, etc), and
#!   these you have to set up separately for every text.  (You may be lucky,
#!   though -- on DBL texts the processing can often pick up this kind of
#!   information direct from DBL's metadata.xml file, so there is no need for
#!   you to transcribe it in such cases.)
#!
#! - Others do reflect the specific text, but you can almost certainly accept
#!   the default values.  For example, stepTextDirection tells STEP whether a
#!   text is written right-to-left or left-to-right, but the default (left to
#!   right) works for most texts.
#!
#! - Some may be language-specific -- for example you may want to set up
#!   definitions for the footnotes which STEP generates under certain
#!   circumstances.  In a similar vein, some define how scripture references
#!   are formatted in this particular text.  This may be specific to the text,
#!   or it may be language related.  (Or you may want to set up defaults for the
#!   language and then override them for particular texts.)
#!
#! - And the remainder are hardly ever going to want to be changed.  A very
#!   little more information about these appears below, and you can find
#!   detailed information in the header comments for each configuration file
#!   in this Resources section.
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
#! as you use the correct format), although there is no need for you to do so;
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
#!
#!
#!
#!
#! Miscellaneous
#! =============
#!
#! * There are some calculated values which can be accessed as though they
#!   were ordinary configuration parameters.  These all have 'calc' in their
#!   key names.  These can be a little difficult to use, because they
#!   become available only after the processing which generates them has
#!   run, so you have to know when it is safe to attempt to access them.
#!
#! * Reference was made above to the StepTextConverterParameters environment
#!   variable.  This is probably best limited just to parameter settings (a=b or
#!   a#=b), and must contain at least two settings:
#!
#!     stepStepOsis2ModFolderPath=somePath; stepCrosswireOsis2ModFolderPath=someOtherPath
#!
#!   which tell the processing where to find the Crosswire and STEP versions of
#!   osis2mod respectively.
#!
#!   You may also find it useful to include a setting:
#!
#!     stepTextConverterDataRoot=someFolderPath
#!
#!   If you do this, then when you supply the command line parameter rootFolder,
#!   the processing will assume this as the start of the path, and you need only
#!   add the remainder.  No particular benefit as far as processing is concerned,
#!   but it will save a little typing, perhaps.
#!
#!   You can add other setting too if you wish.  It will be apparent from above
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
#! - dummyMandatoryValuesForUseWhereSwordConfigHasBeenSupplied.conf: When
#!   working with OSIS as the initial input, rather than USX, there are certain
#!   parameters which we don't need to supply because they normal come from a
#!   previous version of the Sword configuration file.  This file provides dummy
#!   values for parameters whose absence would otherwise bother the processing.
#!
#! - externalDataPaths.conf: Mainly URLs to data which the processing needs to
#!   pick up from the internet.
#!
#! - osis2modVersification.txt: Not a configuration file.
#!
#! - overrides.conf: A convenient place to lodge any overrides which you may
#!   want to apply to everything while testing or debugging.  This file should
#!   normally be empty.
#!
#! - PerOwnerOrganisation: Common information about owner organisations.
#!   Currently limited to Biblica.
#!
#! - PerTextRepositoryOrganisation: Common information about text repositories.
#!   Currently limited to DBL.
#!
#! - _readMeFirst_.txt: This present file.
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
#! - step.conf: You need a copy of this file in the Metadata folder for each
#!   text (or you need the _effect_ of having a copy of it -- there is nothing
#!   to stop you splitting parts of it out into other files, perhaps shared
#!   ones.
#!
#! - strongsCorrections.txt: Not a configuration file.
#!
#! - swordTemplateConfigFile.conf: Used by the processing to determine what a
#!   Sword configuration file should look like.  In the normal course of events,
#!   you should never need to change this.
#!
#! - TextTranslations: Presently contains vernacularTextTranslations_deu.conf
#!   and vernacularTextTranslations_eng.conf, containing respectively the
#!   German and English versions of things like the content of STEP-generated
#!   footnotes.  You can add files of your own -- either here if they are likely
#!   to be used very widely, or in your own structure if you prefer.  If you
#!   include them here, follow the naming convention above -- the fixed prefix
#!   vernacularTextTranslations_ followed by the 3-character language code.  If
#!   you do that, there is no need to mention them explicitly in your
#!   configuration information, because the processing will find them
#!   automatically.  (If you store the files in your own file structure, you
#!   _will_ need to mention them explicitly.)
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
#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
