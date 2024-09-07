/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata


import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.get
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.iso.IsoLanguageAndCountryCodes
import org.stepbible.textconverter.nonapplicationspecificutils.iso.Unicode
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionSilentCommandLineIssue
import java.io.*
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.Path


/******************************************************************************/
/**
 * Reads, stores and makes available configuration data.
 *
 *
 *
 * ## Overview
 *
 * Configuration, it turns out, is both complex and very extensive -- because
 * the various texts potentially differ so much in their character, there are
 * many hundreds of aspects of the processing which we might wish to control
 * without necessarily wanting to change the Kotlin code.  Fortunately it does
 * not follow that we will often *need* to change the vast majority of
 * them; but there are quite a few which will *definitely* differ from one
 * text to another, and we require the flexibility to be able to change many
 * more should that turn out to be necessary.
 *
 * This implies a need to be able to set out a whole raft of defaults, and then
 * to override them as necessary.  We look at this in more detail shortly.
 *
 * A further complicating factor arises because the configuration data is not
 * entirely under our control.  Internally I use a simple home-brew format for
 * configuration data, partly for historical reasons, but also because it makes
 * it easy to override defaults in a fairly logical manner.
 *
 * DBL (the Digital Bible Library), on the other hand, normally makes a lot of
 * metadata available in XML form, and it is convenient to be able to pick up
 * some of the things we need from that without having to transcribe them --
 * which implies the need both to be able to handle XML inputs and to indicate
 * how to take this XML input and store it against the keys which otherwise
 * would be covered by the homebrew-format data.
 *
 *
 *
 *
 * ## General approach
 *
 * To a first approximation (weasel words to which I will return shortly), the
 * homebrew configuration information is made up of a series of key-value
 * pairs.
 *
 * The processing automatically sets up a lot of defaults on your behalf.  You
 * will also need, in your Metadata folder, a file called step.conf.  You
 * use this to define any parameters which absolutely _have_ to be set up on a
 * per-text basis (copyright information being an obvious example), or to
 * override the defaults.  You can put these definitions directly into
 * step.conf, or you can put them into other files which you refer to from
 * step.conf via an 'include' mechanism -- see below.
 *
 *
 *
 *
 *
 * ## The Include mechanism
 *
 * There are two forms of Include statement:
 *
 *     $include filePath
 *     $includeIfExists filePath
 *
 * Each must appear on a line in its own right in a config file, and each works
 * in the same way, the only difference being that the former terminates the
 * processing if the file does not exist, whereas the latter does not.
 *
 * The filePaths here are always *relative* paths.  They can include '.' and
 * '..' in the usual way, and the separators should be given as '/'.  The
 * processing supports a number of special forms:
 *
 * - $root at the start implies the path is relative to the root folder for the
 *   Bible text you are processing.
 *
 * - $metadata implies the path is relative to the Metadata folder for the
 *   text.
 *
 * - $jarResources implies the file lies within the resources section of the
 *   present JAR file, which is where I store a lot of defaults.
 *
 * - Anything else implies the path is relative to the path of the file in
 *   which the $include appears.
 *
 *
 * Include's may be nested to any depth.  Processing proceeds as though all of
 * the included files have been expanded out before it begins.
 *
 * Note, incidentally, that the processing makes no assumptions about the way
 * in which configuration information is, or is not, split across files.  It
 * requires that it *sees* the information in the appropriate order, not that
 * * it sees it in any particular file.  The only requirement is that there be
 * a step.conf file to act as a starting point.
 *
 *
 *
 *
 * ## The other bits
 *
 * Just to fill in the weasel words ('to a first approximation') above ...
 *
 * There are some values which we would like to regard as configuration items,
 * but which have to be determined at run time.  These are stored in the
 * configuration structure, and can indeed therefore be accessed as though they
 * were standard configuration items -- with the proviso that they become
 * available only once the processing needed to generate them has run.
 *
 * Command-line parameters are also stored in the configuration structure.
 * It is possible that configuration files may also attempt to give values to
 * these same parameters.  The values obtained from the command line always
 * take priority.
 *
 *
 *
 *
 *
 * ## Syntax -- overview
 *
 * Each line in a configuration file contains a comment, a definition, a
 * directive, or is blank.
 *
 * A single definition or directive may be continued over more than one line by
 * appending a backslash to the end of it.  Whitespace before a backslash is
 * retained.  Whitespace at the start of a continuation line is ignored.
 *
 *
 *
 *
 *
 * ## Syntax -- comments
 *
 * '#!' is used as a comment marker.  Comments may appear on lines in their own
 * right, or at the end of other lines.  The comment marker and anything to the
 * right of it, and any whitespace to the left of it, is removed before
 * processing begins.  If the result is a blank line -- or if you have any
 * lines which were entirely blank to begin with -- they are ignored.
 *
 * Note that a backslash at the end of a line containing a comment is regarded
 * as being part of the comment -- you can continue a line only if it does
 * *not* contain a comment.
 *
 *
 *
 *
 * ## Definition statements
 *
 * Definition statements are of the form:
 *
 *     key=value or
 *     key#=value
 *
 * The effect is to associate the given key with the given value.  If you
 * encounter two '=' settings for the same key, the later one takes
 * precedence.  Similarly if you have two '#=' settings.  If you have both a
 * '#=' and an '=' setting for the same key, the '#=' one takes precedence.
 *
 * This arrangement lets me start off by storing default settings, and then
 * giving the user the chance to override them.  '#=' gives you a way of
 * forcing a particular value to be used in preference to any 'normal'
 * definitions which will be encountered -- if it gives you a way of forcing
 * things.
 *
 * While reading the configuration data, these definitions are merely stored
 * against their associated key value.  They are evaluated only when something
 * asks to have the value associated with its key.
 *
 * Why does this matter?  Partly because definitions may refer to one another.
 * You can have eg
 *
 *      name=John
 *      .
 *      .
 *      .
 *      hi=Hello ${name}
 *
 * and hi will be given the value 'Hello John'.  By deferring evaluation until
 * the processing actually requires the data, I can cater for the possibility
 * that 'name' may have been defined more than once, and pick up the later
 * definition.  It also means that ordering doesn't matter -- the definition
 * for 'name' may come after that for 'hi' and it will still work.
 *
 * Definitions like this may be nested to any depth, so that you might, for
 * instance, have had:
 *
 *     name = ${firstName} ${surname}
 *
 * I recommend against doing anything too sophisticated, however, or it will
 * get very confusing.
 *
 * And the other reason for deferring things is that, as mentioned earlier,
 * there may be some cases where are dealing with files from a source such as
 * DBL, for which I have processing to read configuration data straight from
 * the metadata files they supply, and deferring makes this easier to handle in
 * a uniform manner.
 *
 *
 *
 *
 *
 * ## Special definition statements
 *
 * There are a few special cases:
 *
 * - There may be multiple lines each starting with the key
 *   vernacularBookDetails. These supply details of book names.  They are
 *   presently used internally only, so I won't go into detail here.
 *
 * - There may be multiple lines each starting with the key
 *   stepUsxToOsisTagTranslation. These are concerned with the (rather large
 *   number of) straightforward mappings from USX to OSIS tags.  More details
 *   are given in usxToOsisTagConversionsEtc.conf.
 *
 * - There may be lines starting $include.  These are directive, and are dealt
 *   with in the next section.
 *
 * - Some definition lines may include @getExternal on their right-hand sides.
 *   These pick up data from an external data source (of which currently only
 *   DBL is supported).  More information about this appears below.
 *
 *
 *
 *
 *
 * ## Directives
 *
 * The only directives currently supported are $include and $includeIfExists.
 * Unlike definition statements, these are actioned immediately they are
 * encountered.  The filePath part is subject to @(...) expansion in the
 * normal manner, but the evaluation occurs at the time the $include directive
 * is encountered, and not at the end of processing.  Note that one side-
 * effect of this is that any other definition to which you refer via ${...}
 * will itself also be evaluated at this point.
 *
 *
 *
 *
 * ## Getting data from external file formats
 *
 * There are some repositories (such as DBL) from which we obtain large numbers
 * of texts, and these (as in the case of DBL) may make metadata available in
 * their own standard form.
 *
 * If a given repository makes sufficient texts available, it may be worthwhile
 * creating special processing to pick up configuration data direct from that
 * repository's own metadata files, rather than have to transcribe it manually.
 * At present we have such processing for DBL (only); and you therefore need
 * to have available a file which explains how to extract the relevant
 * information from there.
 *
 * For details of this, see the file commonForSourceDbl.conf.  In essence, you
 * use special definition statements of the form:
 *
 *     key=@getExternal(metadata, ...)
 *
 * where 'metadata' can be any reasonable name you choose (ie there is no
 * particular significance to the actual name chosen), and serves as a logical
 * name for the file; and the '...' contains the parameters needed by the
 * processing to extract the relevant data from the external file.  (This syntax
 * has been developed purely to cater for the needs of DBL metadata at present,
 * but I'm reasonably hopeful it will carry through to other things, should we
 * ever decide to cater for them.)
 *
 * These definitions are subject to @(...) processing in the usual way, before
 * they are actioned, although I'd recommend not taking advantage of that.
 *
 * If you are going to use this feature, you also need one or more definitions
 * of the form:
 *
 *    stepExternalDataSource=dbl:metadata:$metadata/metadata.xml or
 *    stepExternalDataSourceIfExists=dbl:metadata:$metadata/metadata.xml
 *
 * to associate the external file with the logical name (so that in this case
 * the logical name 'metadata' is associated with $metadata/metadata/xml,
 * using the normal pathname conventions -- see the discussion of $include
 * above).  The 'dbl' portion identifies the type of the data (in this case
 * DBL, which is the only external format supported at the time of writing.
 *
 * The definition is not used until the first time a @getExternal statement is
 * encountered which uses that particular logical name, and it is the value at
 * that particular time which determines the file to be used.  Once a file has
 * been used for the first time, any later attempt to change the association
 * between logical name and file is ignored.
 *
 * You can have as many statements of this form as you need, so there is no
 * problem in picking up information from multiple different files if
 * necessary.  Also, there is nothing to prevent you from assigning two
 * different logical names with the same file, via two different
 * stepExternalDataSource statements.
 *
 * If you use the 'IfExists' form and the file does *not* exist, @getExternal
 * statements will return null.
 *
 * Much more information about the way in which configuration files are handled
 * appears in the header comments to the various default files themselves, so
 * I will not go into further detail here.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigData
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  data class VernacularBookDescriptor
    (
        var ubsAbbreviation: String,
        var vernacularAbbreviation: String,
        var vernacularShort: String,
        var vernacularLong: String
    )





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                 Root folder parsing and module naming                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* You need to store the data for each module under a folder whose name
     follows certain conventions.  The conventions matter because I extract
     information from the folder name.  I do this extraction here in two passes.

     The first is handled by extractDataFromRootFolderName, which extracts basic
     information which may be needed when loading config data.

     The second, handled by generateModuleName, gives the actual module name,
     but cannot be run until significantly later in proceedings.



     Root folder name
     ================

     As I say, the name you choose for the root folder has to follow certain
     conventions.

     All names should start 'Text_ccc_AbBr', where ccc is the 3-character ISO
     language code, in lower case, and AbBr is an abbreviated name for the text,
     and uses whatever uppercase / lowercase combination may seem appropriate.

     Some legacy modules have a suffix -- eg '_th'.  Where this is the case,
     you should give that suffix next (including the preceding underscore).

     And then after this, you may give an additional suffix, again preceded
     by an underscore -- '_public' for '_onlineOnly' for modules which must
     not be made available offline.

     You can optionally also give '_step' for modules which can be used only
     within STEP, although this is the default, and is therefore optional.

     A few examples:

       Text_eng_KJV_public: An English language text with the abbreviated name
         'KJV', which can be turned into a publicly available module.  (By this
         I mean that we are free to generate a module which can then be offered
         in our repository for use even outside of STEP.)

       Text_ger_HFA or Text_ger_HFA_step: A German language text with the
         abbreviated name HFA which must give rise to a module which is used
         only within STEP.

       Text_ger_XyZ_onlineOnly: A German language text with the abbreviated name
         XyZ, where permissions limit us to creating only a STEP-internal
         version which we are not permitted to make available in offline STEP.



     Language code
     -------------

     The language code should be the three character ISO code, all lowercase.
     Some languages have more than one ISO code, and here we normally have our
     own preference -- 'ger' rather than 'deu' for instance.  Note that where
     this is the case, our preference may not always reflect either the ISO
     preference or the code preferred by the translators.

     At present we have not given any consideration to how to handle things if
     it is ever necessary to hve a country-specific language code (eg Latin
     American Spanish).



     Abbreviated name
     ----------------

     This is complicated.

     If we are given an abbreviated name (as is the case with DBL, where the
     abbreviation is given in the metadata), we normally go with what we are
     given -- the vernacular abbreviation if that exists and is in Latin
     characters, or else the English abbreviation.

     There are exceptions to this, however:

     - Most / all of the texts we have seen from the SeedCompany use the
       language code as the abbreviated name for the text.  Where this is
       the case, we give the abbreviated name as 'SC' for SeedCompany.

     - Ditto ULB texts.  Here we give the abbreviated name as ULB.

     - Biblica have a collection of copyright texts (where we follow the
       initial rule above, and take the abbreviation straight from the
       metadata), and a collection of open access texts.  There is a lot of
       overlap between the two, and where this is the case I imagine
       ultimately we will move to using the open access version.  Pro tem,
       though, the open access texts usually have an 'O' (for Open) in the
       abbreviated name (and an equivalent letter in the vernacular form),
       which we don't want.  Where a corresponding copyright version exists,
       therefore, we take the abbreviation from that for use with the open
       access version.  Where there is no corresponding copyright version,
       I try to use Google Translate to help me work out which letter to
       remove.


     This still leaves some cases where we have no abbreviation (because one
     has not been supplied); or where the abbreviation looks to have been a
     typo; or where the abbreviation duplicates the language code but is not
     covered by the exceptions above.

     Here, we have to work something out for ourselves.  bible.com may help,
     or open.bible, but it will be a case of a fair bit of spade work.

     Oh yes, and finally there may be a few odd ones where the code below
     contains a mapping to force the abbreviation to the 'right' thing because
     historically we haven't properly followed our own rules, and can't afford
     to start doing so now.



     Module and file names -- STEP-internal
     ======================================

     Probably best done by example.

     Text_fra_BDS gives rise to a MODULE called FraBDS.  This is true also of
     Text_fra_BDS_step -- as mentioned above, the _step is optional, because the
     default is that modules are intended for use only within STEP.  And it is
     true too of Text_fra_BDS_onlineOnly.

     Note that the language code is converted to uppercase-lowercase-lowercase.

     The language code is suppressed on English language and ancient language
     texts.

     Modules are held in a zip file.  The zip file has a name of the form
     <moduleName>.zip, where <moduleName> duplicates the module name exactly.

     The Sword config file appears within this zip file.  Its name is of the
     form <lcModuleName>.conf, where lcModuleName is the module name, forced to
     lowercase.  (This is not a complication of our own choosing: it is a
     requirement specified by Crosswire.)

     The first 'active' line of the Sword config file is of the form
     [<moduleName>].  Here moduleName is the name of the module _not_ forced to
     lowercase.

     All of the files associated with the module are collected together into a
     file destined for our repository.  The name of this file is either

       forRepository_<moduleName>_S.zip or
       forRepository_<moduleName>_S_onlineUsageOnly.zip

     where the _S indicates that the module is for use only within STEP, and the
     onlineOnly means what it looks as though it means.



     Module and file names -- Public
     ===============================

     These work pretty much as above, but with a few minor exceptions:

     - The language code is never suppressed.

     - The language code is always followed by an underscore in the module name.
       (This gives us a subtle means of distinguishing public and STEP-internal
       modules: the public ones have an underscore as their fourth character.)

     - The repository files have names ending in _P (for Public) rather than _S.
  */

  fun extractDataFromRootFolderName ()
  {
    /**************************************************************************/
    val C_SpecialNaming =
      listOf("ArbKEH" to "NAV",
              "ChiCCB" to "CCB",
               "PesPCB" to "FCB",
                "PorNVI" to "PNVI",
                 "RonNTR" to "NTLR",
                  "RusNRT" to "NRT",
                   "SpaNVI" to "NVI",
                    "SwhNEN" to "Neno")



    /**************************************************************************/
    val parsedFolderName = "Text_(?<languageCode>...)_(?<abbreviation>[^_]+)(_(?<rest>.*)?)?".toRegex().matchEntire(FileLocations.getRootFolderName())
    var languageCode = canonicaliseLanguageCode(parsedFolderName!!.groups["languageCode"]!!.value)
    var abbreviatedName = parsedFolderName.groups["abbreviation"]!!.value
    val rest = parsedFolderName.groups["rest"]?.value
    var legacySuffix = ""
    var operationalSuffix = ""

    if (null != rest)
    {
      val x = rest.split("_")
      if (2 == x.size)
      {
        legacySuffix = x[0]
        operationalSuffix = x[1].lowercase()
      }
      else
      {
        val lc = x[0].lowercase()
        if ("step" in lc || "onlineonly" in lc || "public" in lc)
        {
          operationalSuffix = lc
          legacySuffix = ""
        }
        else
          legacySuffix = x[0]
      }
    }



    /**************************************************************************/
    /* Work out the target audience (STEP-only or public).  This comes either
       from the root folder name or from a command-line argument.

       If the root folder name states public or step unambiguously then we use
       that (and the command-line argument is optional, but if given must state
       the same as the folder name).

       If the root folder states both public and step, then the command-line
       argument must be present and we go with that.

       If the root folder states neither, it is as though it stated step -- see
       above. */

    val mayBePublic   = "public" in operationalSuffix
    val mayBeStepOnly = "step"   in operationalSuffix
    val optionFromCommandLine = CommandLineProcessor["targetAudience"]?.first()?.uppercase()
    val targetAudience: String
    if (mayBeStepOnly && mayBePublic)
    {
       if (null == optionFromCommandLine) throw StepExceptionSilentCommandLineIssue("Could be public or STEP-only run.  Need targetAudience on command line to indicate which.")
       targetAudience = optionFromCommandLine
    }

    else if (mayBeStepOnly)
    {
      targetAudience = "S"
      if (null != optionFromCommandLine && "S" != optionFromCommandLine)
        throw StepExceptionBase("Folder name implies this is a STEP-only build, but targetAudience on the command line says otherwise.")
    }

    else if (mayBePublic)
    {
      targetAudience = "P"
       if (null != optionFromCommandLine && "P" != optionFromCommandLine)
         throw StepExceptionBase("Folder name implies this is a public build, but targetAudience on the command line says otherwise.")
    }

    else
    {
      targetAudience = "S"
      if (null != optionFromCommandLine && "S" != optionFromCommandLine)
        throw StepExceptionBase("Folder name implies this is a STEP-only build, but targetAudience on the command line says otherwise.")
    }

    deleteAndPut("stepTargetAudience", targetAudience, true)
    deleteAndPut("stepOnlineUsageOnly", if ("onlineonly" in operationalSuffix) "Yes" else "No", true)



    /**************************************************************************/
    /* Legacy code -- can't recall why I had this.

       By this point, I've extracted the abbreviated name from the folder name.
       However, it appears that in some cases, the folder name may be wrong,
       and I wanted to have the opportunity to override it.

       I'm assuming I may need to retain this -- and that at the least, that
       retaining it will have no adverse consequences. */

    val x = get("stepAbbreviationVernacular")
    abbreviatedName = if (null != x && StepStringUtils.isAsciiCharacters(x)) x else get("stepAbbreviationEnglish") ?: abbreviatedName



    /**************************************************************************/
    if (legacySuffix.isNotEmpty()) legacySuffix = "_$legacySuffix"
    languageCode = StepStringUtils.sentenceCaseFirstLetter(languageCode)

    val revisedAbbreviation = C_SpecialNaming.find { it.first == languageCode + abbreviatedName } ?.second
    if (null != revisedAbbreviation)
      abbreviatedName = revisedAbbreviation



    /**************************************************************************/
    val moduleName: String =
      if ("P" == targetAudience)
        languageCode + "_" + abbreviatedName + legacySuffix
      else
      {
        if (languageCode in listOf("Eng", "Grc", "Hbo"))
          languageCode = ""

        languageCode + abbreviatedName + legacySuffix
      }



    /**************************************************************************/
    delete("stepModuleName"); put("stepModuleName", moduleName, force = true)
  }


    /**************************************************************************/
    /**************************************************************************/
    /**                                                                      **/
    /**                              Data-load                               **/
    /**                                                                      **/
    /**************************************************************************/
    /**************************************************************************/

    /****************************************************************************/
    private val m_AlreadyLoaded: MutableSet<String> = mutableSetOf()
    private var m_SampleText = ""


    /****************************************************************************/
    /**
    * We need some sample text in order to be able to assess text direction when
    * it is not supplied overtly.  This enables the text to be set.
    *
    * @param text
    */

    fun setSampleText (text: String)
    {
      m_SampleText = text
    }


    /****************************************************************************/
    /**
     * Loads metadata
     *
     * @param rootConfigFilePath The configuration file.  If the name starts
     *   '$jarResources/', it is assumed that it names a file within the resources
     *   section of the present JAR file.  Otherwise, it is taken as being an
     *   actual path name.
     */

    fun load (rootConfigFilePath: String)
    {
        if (m_Initialised) return // Guard against multiple initialisation.
        m_Initialised = true
        val configFilePath = if (File(rootConfigFilePath).isAbsolute) rootConfigFilePath else Paths.get(FileLocations.getMetadataFolderPath(), rootConfigFilePath).toString()
        load(configFilePath, false) // User overrides.
        loadDone()
    }


    /****************************************************************************/
    /* Need this as a separate function because $include's involve recursive
       calls. */

    private fun load (configFilePath: String, okIfNotExists: Boolean)
    {
        /**************************************************************************/
        // Dbg.d("Loading config file: " + configFilePath)



        /**************************************************************************/
        /* Originally I would have regarded any attempt to load the same file twice
           as probably indicating an error.  However, it is convenient to accept
           this and simply not load the file a second time.  */

        val inputPath = FileLocations.getInputPath(configFilePath)
        if (m_AlreadyLoaded.contains(inputPath)) return
        m_AlreadyLoaded.add(inputPath)



        /**************************************************************************/
        val modifiedConfigFilePath = FileLocations.getInputPath(configFilePath)
        val sharedConfigFolderPath = FileLocations.getSharedConfigFolderPath()
        if (Path(modifiedConfigFilePath).startsWith(Path(sharedConfigFolderPath)))
        {
          val x = Path(modifiedConfigFilePath.substring(sharedConfigFolderPath.length))
          m_SharedConfigFolderPathAccesses.add(Paths.get(sharedConfigFolderPath, x.getName(0).toString()).toString())
        }

        ConfigFilesStack.push(modifiedConfigFilePath)

        for (x in getConfigLines(modifiedConfigFilePath, okIfNotExists))
        {
          //Dbg.d("$configFilePath: $x")
          val line = x.trim().replace("@home", System.getProperty("user.home"))
          if (processConfigLine(line, modifiedConfigFilePath)) continue // Common processing for 'simple' lines -- shared with the method which extracts settings from an environment variable.
          throw StepExceptionBase("Couldn't process config line: $line")
        } // for

        ConfigFilesStack.pop()


        /**************************************************************************/
        // Dbg.d("Finished loading config file: " + configFilePath)
    }


    /****************************************************************************/
    /**
    * I make provision for parameters to be stored in an environment variable
    * named StepTextConverterParameters.  The format is:
    *
    *   setting;setting;setting; ...
    *
    * where individual settings look as they would in a config file -- key=val.
    * If you need a semicolon within a setting, escape it using \;.  If you need
    * a backslash, escape it as \\.
    *
    * Clearly you're not going to want to store too many settings this way, but
    * there may be things -- such as the location of osis2mod -- which is more
    * easily handled like this, rather than storing it in config files.
    */

    fun loadFromEnvironmentVariable ()
    {
      ConfigFilesStack.push("StepTextConverterParameters environment variable")

      var parmList = System.getenv("StepTextConverterParameters") ?: return
      parmList = parmList.replace("\\\\", "\u0001").replace("\\;", "\u0002")
      val settings = parmList.split(";").map { it.trim().replace("\u0001", "\\").replace("\u0002", ";") }
      settings.forEach {
        if (!processConfigLine(it, ""))
          throw StepExceptionBase("Couldn't parse setting from environment variable: $it")
      }

      ConfigFilesStack.pop()
    }


  /****************************************************************************/
  private fun canonicaliseLanguageCode (rawLanguageCode: String): String
  {
    val languageCode = IsoLanguageAndCountryCodes.get3CharacterIsoCode(rawLanguageCode)
    delete("stepLanguageCode3Char"); put("stepLanguageCode3Char", languageCode, force = true)
    delete("stepLanguageCode2Char"); put("stepLanguageCode2Char", IsoLanguageAndCountryCodes.get2CharacterIsoCode(languageCode).ifEmpty { languageCode }, force = true)
    return languageCode
  }


    /****************************************************************************/
    /* Obtains the configuration lines from a given file, which may be specified
       as being relative to the root folder, relative to the calling file (if
       supplied) or within the JAR.

       The returned list will have had comments and blank lines removed and
       continuation lines combined into a single line.

       I also replace *= by =.  *= was used under particular circumstances in the
       old config files.  I no longer need it, but I don't want to have to change
       all of the old files.
     */

    private fun getConfigLines (configFilePath: String, okIfNotExists: Boolean): List<String>
    {
        /************************************************************************/
        try
        {
            val rawLines = FileLocations.getInputStream(configFilePath)!!.bufferedReader().use { it.readText() } .lines()
            val lines: MutableList<String> = rawLines.map{ it.split("#!")[0].trim() }.filter{ it.isNotEmpty() }.toMutableList() // Ditch comments and remove blank lines.
            for (i in lines.size - 2 downTo 0) // Join continuation lines.
                if (lines[i].endsWith("\\"))
                {
                    lines[i] = lines[i].substring(0, lines[i].length - 1) + lines[i + 1].replace(Regex("^\\s+"), "")
                    lines[i + 1] = ""
                }

            return lines.filter { it.isNotEmpty() }
        }
        catch (_: Exception)
        {
            if (!okIfNotExists) throw StepExceptionBase("Could not find config file $configFilePath")
            return listOf()
        }
    }


    /****************************************************************************/
    /**
     * Called when all defaults and metadata have been handled.
     * <p>
     *
     * The method also checks that mandatory parameters have been supplied, and
     * sets to an empty string any optional parameters which have not been
     * supplied.
     */

    private fun loadDone()
    {
        /**************************************************************************/
        /* Convert any saved tag translation details to usable form. */

        generateTagTranslationDetails()
    }


    /****************************************************************************/
    /* Parses a line so as to determine the key and value, and stores them. */

    private fun loadParameterSetting (line: String)
    {
        val force = line.contains("#=")
        val parts = line.split(Regex(if (force) "\\#\\=" else "\\="), 2)
        put(parts[0].trim(), parts[1].trim(), force)
    }


    /****************************************************************************/
    /* This caters for that subset of configuration lines which can reasonably
       turn up both in config files and in the converter's environment
       variable. */

    private fun processConfigLine (directive: String, callerFilePath: String): Boolean
    {
      /**************************************************************************/
      if (directive.isEmpty())
        return true

      val lineLowerCase = directive.lowercase()
      //Dbg.dCont(directive, "stepAboutAsSupp")



      /**************************************************************************/
      if (lineLowerCase.startsWith("\$include"))
      {
        var newFilePath = directive.replace("${'$'}includeIfExists", "")
        newFilePath = newFilePath.replace("${'$'}include", "").trim()
        newFilePath = expandReferences(newFilePath, false)!!
        if (!newFilePath.startsWith("$") && !File(newFilePath).isAbsolute)
          newFilePath = Paths.get(File(callerFilePath).parent, newFilePath).toString()
        load(newFilePath, lineLowerCase.contains("exist"))
        return true
      }



      /**************************************************************************/
      if (lineLowerCase.startsWith("#vernacularbookdetails"))
      {
        processVernacularBibleDetails(directive)
        return true
      }



      /**************************************************************************/
      if (directive.matches(Regex("(?i)stepUsxToOsisTagTranslation.*")))
      {
        saveUsxToOsisTagTranslation(directive)
        return true
      }



      /**************************************************************************/
      if (directive.matches(Regex("(?i)stepExternalDataSource.*")))
      {
        ConfigDataExternalFileInterface.recordDataSourceMapping(directive, callerFilePath)
        return true
      }



      /**************************************************************************/
      if (directive.matches(Regex("(?i)copyAsIs.*")))
      {
        m_CopyAsIsLines.add(directive.substring(directive.indexOf("=") + 1))
        return true
      }



      /**************************************************************************/
      /* Retained for backward compatibility. */

      if (directive.matches(Regex("(?i)stepRegex.*")))
      {
        processRegexLine(m_RegexesForGeneralInputPreprocessing, directive)
        return true
      }



      /**************************************************************************/
      if (directive.matches(Regex("(?i)stepRegexForGeneralInputPreprocessing.*")))
      {
        processRegexLine(m_RegexesForGeneralInputPreprocessing, directive)
        return true
      }



      /**************************************************************************/
      if (directive.matches(Regex("(?i)stepRegexForForcedOsisPreprocessing.*")))
      {
        processRegexLine(m_RegexesForForcedOsisPreprocessing, directive)
        return true
      }



      /**************************************************************************/
      if (directive.contains("=")) // I think this should account for all remaining lines.
      {
        var x = directive.replace("(?i)\\.totextdirection\\s*\\(\\s*\\)".toRegex(), ".#toTextDirection")
        x = x.replace("(?i)\\.todate\\s*\\(\\s*\\)".toRegex(), ".#toDate")
        loadParameterSetting(x)
        return true
      }



      /**************************************************************************/
      return false // In other words, we haven't managed to process it.
    }


    /****************************************************************************/
    /* Takes a regex definition, splits it into its two elements (pattern and
       replacement) and adds it to the regex collection.  Regexes represent
       pattern match and replacements which are applied to incoming USX before
       processing. */

    private fun processRegexLine (collection: MutableList<Pair<Regex, String>>, line: String)
    {
      val x = line.substring(line.indexOf("=") + 1)
      val (pattern, replacement) = x.split("=>")
      collection.add(Pair(tidyVal(pattern).toRegex(), tidyVal(replacement)))
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                              Get and put                               **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * When making changes to derived works, we may be required to add details of
    * the changes we have made.  The present method adds a given text.  It is
    * intended to cope with the possibility that different parts of the processing
    * may need to add to this collection.
    *
    * @param text
    */

    fun addDetailsOfDerivedWork (text: String)
    {
      var s = get("stepChangesMadeByUsInDerivedWork", "")
      if (text in s) return
      s += "  $text"
      delete("stepChangesMadeByUsInDerivedWork")
      put("stepChangesMadeByUsInDerivedWork", s, false)
    }


    /****************************************************************************/
    /**
    * For use only by ConfigDataSupport.  We need to know if something is the
    * name of an existing configuration parameter or not, but we don't want to
    * look the thing up 'properly' and risk it being calculated and assigned a
    * value at this point.
    *
    * @param purportedKey
    * @return Value associated with key, or null if not a key.
    */

    fun checkIfIsParameterKey (purportedKey: String) = m_Metadata[purportedKey]


    /****************************************************************************/
    /**
     * Deletes a given key/value pair.  Intended mainly for use during command
     * line processing, where I need to be able to force things to their default
     * values, but then override these forced values with new forced values for
     * things which are not being defaulted.
     *
     * @param key
     */

    fun delete (key: String)
    {
      if (key in m_Metadata) m_Metadata.remove(key)
    }


    /****************************************************************************/
    /* Deletes any existing setting for a parameter and then applies a new
     * setting.
     *
     * A little care my be required here in order to understand the 'force'
     * setting.  The effect of deleteAndPut is to forcibly replace any existing
     * setting with a new value.  The 'force' parameter says whether that new
     * setting is to be regarded as a forcible one, in the sense that no attempt
     * to revise it (other than another call to deleteAndPut) will replace it.
     *
     * @param key Key.
     * @param theValue Associated value.
     * @param force If true, later calls for this same key are ignored.
     *
     */

    fun deleteAndPut (key: String, theValue: String, force: Boolean)
    {
      delete(key)
      put(key, theValue, force)
    }


    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or returns null if the key
    * has no associated value.
    *
    * @param key
    * @return Associated value or null.
    */

    operator fun get (key: String): String?
    {
      //Dbg.d(key, "stepBibleDescriptionAsItAppearsOnBiblePicker")
      //Dbg.d(key)
      return getInternal(key, true)
    }


    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or returns the default if the
    * key has no associated value.
    *
    * @param key
    * @para dflt
    * @return Associated value or null.
    */

    fun get (key: String, dflt: String): String
    {
      return getInternal(key, true) ?: dflt
    }


    /****************************************************************************/
    /**
    * The configuration data may include lines which are simply to be copied
    * as-is to the Sword configuration file.  This returns that list.
    *
    * @return As-is lines.
    */

    fun getCopyAsIsLines (): List<String> = m_CopyAsIsLines



    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or throws an exception if the
    * key has no associated value.
    *
    * @param key
    * @return Associated value if any.
    */

    fun getOrError (key: String): String
    {
      return get(key) ?: throw StepExceptionBase("No metadata found for $key ${ConfigDataSupport.getDescriptorAsString(key)}.")
    }



    /****************************************************************************/
    /**
    * Obtains the value associated with a given key, or throws an exception if
    * the key has no associated value.  The value is assumed to be a string
    * representation of a Boolean -- Y(es) or T(rue) or anything else.  Returns
    * true if the value starts with Y or T (not case-sensitive).
    *
    * @param key
    * @return True or false.
    */

    fun getAsBoolean (key: String): Boolean
    {
      var s = getOrError(key)
      if (s.isEmpty()) return false
      s = s.substring(0, 1).uppercase()
      return s == "Y" || s == "T"
    }


    /****************************************************************************/
    /**
    * Obtains the value associated with a given key, or uses a default if
    * the key has no associated value.  The value is assumed to be a string
    * representation of a Boolean -- Y(es) or T(rue) or anything else.  Returns
    * true if the value starts with Y or T (not case-sensitive).
    *
    * @param key
    * @param dflt Default value if key has no associated value.
    * @return True or false.
    */

    fun getAsBoolean (key: String, dflt: String): Boolean
    {
      val s = (get(key) ?: dflt).substring(0, 1).uppercase()
      return s == "Y" || s == "T"
    }


    /****************************************************************************/
    /**
    *  Returns all of the keys from the metadata.
    *
    *  @return Keys.
    */

    fun getKeys () : Set<String> = m_Metadata.keys


    /****************************************************************************/
    /**
    *  Returns all of the keys from the metadata which are matched by a given
    *  function.
    *
    *  @param matcher
    *  @return Keys.
    */

    fun getMatchingKeys (matcher: (String) -> Boolean) = m_Metadata.keys.filter{ matcher(it) }.toSet()


    /****************************************************************************/
    /**
    *  Returns all of the keys from the metadata related to regex preprocessing
    *  for this particular flavour of run.
    *
    *  @return Keys.
    */

    fun getPreprocessingRegexes () =
      if (getAsBoolean("startProcessFromOsis", "no"))
        m_RegexesForForcedOsisPreprocessing
      else
        m_RegexesForGeneralInputPreprocessing


    /****************************************************************************/
    /**
    *  Returns all of the keys from the metadata related to XSLT preprocessing
    *  for this particular flavour of run.
    *
    *  @return Keys.
    */

    fun getPreprocessingXslt () =
      if (getAsBoolean("startProcessFromOsis", "no"))
        get("stepXsltStylesheetForForcedOsisPreprocessing")
      else
        get("stepXsltStylesheetForGeneralInputPreprocessing")


    /****************************************************************************/
    /**
     *  Checks if a given key corresponds to translatable text which has no
     *  vernacular override, and is therefore in English.
     *
     *  @return True if this is the English version of a piece of translatable
     *    text.
     */

    fun isEnglishTranslatableText (key: String) = m_EnglishDefinitions.contains(key)


    /****************************************************************************/
    /**
     * Stores a given key / value pair, or overwrites an existing one.
     *
     * @param key Key.
     * @param theValue Associated value.
     * @param force If true, later calls for this same key are ignored.
     *
     */

    fun put (key: String, theValue: String, force: Boolean)
    {
      /************************************************************************/
      //Dbg.d("$key = $theValue (Force=$force)")



      /************************************************************************/
      /* Dummy placeholder -- parameters may be included in files for reference
         purposes, but given the value '%%%UNDEFINED', in which case it is as
         though they had not been mentioned at all. */
         
      if ("%%%UNDEFINED" == theValue)
        return



      /************************************************************************/
      ConfigDataSupport.reportIfMissingDebugInfo(key, "Put")



      /************************************************************************/
      /* If this is a 'force' setting and we already have a force setting, we
         retain the existing one. */

      if (force && key in m_Metadata && m_Metadata[key]!!.m_Force)
      {
         ConfigDataSupport.reportSet(key, theValue, ConfigFilesStack.getSummary(), "Skipped because forced value already in effect")
         return
      }



      /************************************************************************/
      /* If we're forcing, then it's always ok to write to the store because
         force overrides any existing non-force value.  If we're _not_ forcing,
         then it's ok to write only if an entry does not already exist, or if
         any existing entry was not forced.  In other words, in general,
         later wins. */

      if (!force)
      {
        val tmp = m_Metadata[key]  // If we're not forcing, it's ok to store the new data if either there's no existing entry, or the existing one is not marked force.
        if (null != tmp && tmp.m_Force)
        {
          ConfigDataSupport.reportSet(key, theValue, ConfigFilesStack.getSummary(), "Skipped because forced value already in effect")
          return
        }
      }



      /************************************************************************/
      /* Sort out special markers. */

      val value = tidyVal(theValue)



      /************************************************************************/
      ConfigDataSupport.reportSet(key, theValue, ConfigFilesStack.getSummary(), null)
      m_Metadata[key] = ParameterSetting(value, force)
    }


    /****************************************************************************/
    /* This should be called only directly from the external-facing methods or
       when expanding @-references and we have a new value which needs to be
       looked up. */

    private fun getInternal (key: String, nullsOk: Boolean): String?
    {
      /************************************************************************/
      /* CAUTION: With something like @(stepVersificationScheme, NRSV), "NRSV"
         is received here as though it were the key for an item of config data,
         when in fact, of course, it's simply a default value for the @(...).
         reportIfMissingDebugInfo therefore needs to be able to cater for this
         possibility, and not treat it as a key. */

      ConfigDataSupport.reportIfMissingDebugInfo(key, "Get")



      /************************************************************************/
      val calculated = getCalculatedValue(key)
      if (null != calculated && "@get" !in calculated) return calculated



      /************************************************************************/
      val parmDetails = m_Metadata[key] ?: return null
      if (null != parmDetails.m_Value && parmDetails.m_Value!!.startsWith("%%%"))
        throw StepExceptionBase("ConfigData.get for $key: value was recorded as ${parmDetails.m_Value} and no value has been supplied.")

      return expandReferences(parmDetails.m_Value, nullsOk)
    }


    /****************************************************************************/
    /**
    * Replaces any existing value.
    *
    * @param key Key.
    * @param value Associated value.
    * @param force If true, later calls for this same key are ignored.
    */

    fun replace (key: String, value: String, force: Boolean = false)
    {
      delete(key)
      put(key, value, force)
    }


    /****************************************************************************/
    /**
    * Sets a value, without forcing.
    *
    * @param key
    * @param value
    */

    operator fun set (key: String, value: String)
    {
      put(key, value, false)
    }





    /**************************************************************************/
    /**************************************************************************/
    /**                                                                      **/
    /**                  Expansion of embedded references                    **/
    /**                                                                      **/
    /**************************************************************************/
    /**************************************************************************/

    /**************************************************************************/
    /**
     * Takes a string possibly containing @(...) and / or @getExternal(...) and
     * returns the expanded form.
     *
     * nullsOk is relevant only where we are obtaining a single value.  If
     * we have multiple top level values being concatenated, a null is always
     * an error.
     *
     * @param theLine Line to be processed.
     * @param nullsOk True if a null value is ok.  (Throws an exception otherwise.)
     * @return Resulting value.
     */

    fun expandReferences (theLine: String?, nullsOk: Boolean): String?
    {
      val errorStack: Stack<String> = Stack()

      try
      {
        //Dbg.d(theLine ?: "")
        return expandReferencesTopLevel(theLine, nullsOk, errorStack)
      }
      catch (_: StepExceptionBase)
      {
        throw StepExceptionBase("ConfigData error parsing: " + errorStack.joinToString(" -> "))
      }
    }


    /**************************************************************************/
    private val C_Pat_ExpandReferences = "(?i)(?<at>(@|@getExternal|@choose|@getTranslation))\\(\\.(\\d\\d\\d)\\.(?<content>.*?)\\.\\3\\.\\)(?<additionalProcessing>(\\.#\\w+))?".toRegex()
    private val C_Pat_ExpandReferences_AdditionalProcessing = "(?<additionalProcessing>(\\.#\\w+))".toRegex()



    /**************************************************************************/
    /* Processes a single @-thing.  These are characterised by the fact that
       they contain one or more elements, comma-separated, and we run across the
       list until we find one that returns non-null.  In addition, they may
       optionally contain a fixed string by way of default.  If present, this
       will be preceded by '='.  Note that the default value is _not_ expanded
       out -- it is taken as a fixed string. */

    private fun expandReferenceAtThing (at: String, theLine: String, additionalProcessing: String?, errorStack: Stack<String>): String?
    {
      /************************************************************************/
      val C_Evaluate = 1
      val C_Choose = 2
      val C_GetExternal = 3
      val C_GetTranslation = 4
      val atType =
        when (at.lowercase())
        {
          "@" -> C_Evaluate
          "@choose" -> C_Choose
          "@gettranslation" -> C_GetTranslation
          else -> C_GetExternal
        }
      //Dbg.d(C_Choose == atType)



      /************************************************************************/
      errorStack.push("[expandReferenceAlternative: $theLine]")



      /************************************************************************/
      /* Split off the default, if any. */

      val (line, dflt) = if ("=" in theLine) theLine.split("=").map { it.trim() } else listOf(theLine, null)
      var args = splitStringAtCommasOutsideOfParens(line!!)



      /************************************************************************/
      /* Deal with getTranslation, which is of the form

           @getTranslation(key)   OR
           @getTranslation(key, eng)

         I don't really do any checking here: if there is more than one
         argument, I simply assume that it is the second form above.
      */

      if (C_GetTranslation == atType)
        return TranslatableFixedText.lookupText(if (1 == args.size) Language.Vernacular else Language.English, args[0])



      /************************************************************************/
      /* If this is a getExternal, the first element is the file selector. */

      var fileSelector: String? = null
      if (C_GetExternal == atType)
      {
        fileSelector = args[0]
        args = args.subList(1, args.size)
      }



      /************************************************************************/
      /* This takes a single argument to the @-thing, and expands this one
         argument -- not to evaluate any value associated with it, but to see
         if it, itself, involves @-things, in which case these @-things are
         expanded out.  This leaves us with the actual value which can be
         used to evaluate the @-thing.  Note that this will never return a
         null -- the caller is guaranteed to have _something_ which can be
         evaluated. */

      fun expandArgument (arg: String): String?
      {
        val res = expandReferencesTopLevel(arg, false, errorStack) ?: return null // Expand out the argument itself.
        return if (C_Choose == atType) res else expandReferenceIndividualElement(fileSelector, res, errorStack)
      }



      /************************************************************************/
      /* Run over the individual elements until we find a non-null. */

      var res: String? = null
      for (arg in args)
      {
        res = expandArgument(arg) // See if the argument contains any @-things to be expanded.  res will always be non-null.

        if (null != res) // If we've found a value. there's no need to evaluate further, but we do need to see if the value needs to be post-processed.
        {
          if (null != additionalProcessing)
            res = specialistProcessing(res, additionalProcessing.replace(".#", ""))
          break
        }
      }



      /************************************************************************/
      errorStack.pop()
      return res ?: dflt
  }


  /**************************************************************************/
  private fun expandReferenceIndividualElement (fileSelector: String?, elt: String, errorStack: Stack<String>): String?
  {
    errorStack.push("[expandReferenceAlternative: $elt]")
    val res = if (null == fileSelector) get(elt) else ConfigDataExternalFileInterface.getData(fileSelector, elt)
    errorStack.pop()
    return res
  }


    /**************************************************************************/
    /* Handles a string which may contain one or more @-things at the top level.
       Expands all of them. */

    private fun expandReferencesTopLevel (theLine: String?, nullsOk: Boolean, errorStack: Stack<String>): String?
    {
      /************************************************************************/
      if (null == theLine)
        return null



      /************************************************************************/
      var line = tidyVal(theLine) // Replace {space} by ' ' etc.



      /************************************************************************/
      //Dbg.dCont(line, "MinimumVersion=")



      /************************************************************************/
      /* Nothing to do unless the string contains "@(" or "@getExternal(". */

      if ("@(" !in line && "@getExternal(" !in line && "@choose(" !in line && "@getTranslation(" !in line)
        return line



      /************************************************************************/
      //$$$line = line.replace("\\(", "JamieBra").replace("\\)", "JamieKet")



      /************************************************************************/
      errorStack.push("[expandReferencesConsecutive: $line]")



      /************************************************************************/
      /* Mark the corresponding sets of parens so that balanced parens can be
         identified -- eg '(.001.   .001.)' */

      line = StepStringUtils.markBalancedParens(line)



      /************************************************************************/
      /* Called when we have an @-thing to expand.  Parses the @-thing to
         determine if it's @(), @getExternal(), etc and to obtain its content.
         Removes from the content the markers introduced by
         StepStringUtils.markBalancedParens because this method may be called
         recursively on the content, and we don't want the markers to confuse
         things.  Then expands the @-thing. */

      fun evaluate (details: String, additionalProcessing: String?): String?
      {
        val mr = C_Pat_ExpandReferences.find(details) ?: return details
        val at = mr.groups["at"]!!.value
        var content = mr.groups["content"]!!.value
        content = content.replace("\\.\\d\\d\\d\\.".toRegex(), "")
        return expandReferenceAtThing(at, content, additionalProcessing, errorStack)
     }



     /*************************************************************************/
     /* Repeatedly looks for the next @-thing in the string and then arranges
        to parse it and replace it by its expanded value. */

     while (true)
     {
       var at = "@(" // Look for @(.
       var ixLow = line.indexOf(at)
       if (-1 == ixLow)
       {
         at = "@getExternal("
         ixLow = line.indexOf(at) // If @( wasn't found, look for @getExternal( instead.
       }
       if (-1 == ixLow)
       {
         at = "@choose("
         ixLow = line.indexOf(at) // If @getExternal( wasn't found, look for @choose( instead.
       }

       if (-1 == ixLow) // If we didn't find any @-things, there's nothing else to expand.
         break

       val atLength = at.length
       val marker = line.substring(ixLow + atLength, ixLow + atLength + 5) // Get the start marker eg .001.
       val ixHigh = line.indexOf(marker, ixLow + at.length + 1) + marker.length + 1 // Points to the corresponding end marker.

       val pre = line.substring(0, ixLow) // The bit before the @-thing.
       val content = line.substring(ixLow, ixHigh) // The argument list within the @-thing.
       var post = line.substring(ixHigh) // The bit after the @-thing.

       var additionalProcessing: String? = null // Check to see if there's any additional processing -- eg .#toDate.
       val mr = C_Pat_ExpandReferences_AdditionalProcessing.find(post)
       if (null != mr)
       {
         additionalProcessing = mr.groups["additionalProcessing"]!!.value
         post = post.substring(additionalProcessing.length) // Knock the additionalProcessing off the text which followed the @-thing.
       }

       line = pre + (evaluate(content, additionalProcessing) ?: "\b") + post // I use \b to flag the fact that we've had a null value.
     }



     /*************************************************************************/
     /* I used \b above to indicate that something has returned a null.  If the
        processed line comprises just a \b, then the overall value is null. */

     var res = if ("\b" == line) null else line



     /*************************************************************************/
     /* If the overall result is a null, then it's an error if we actually have
        just a null.  Otherwise, it's an error if we have a null anywhere in
        the string, because we've been concatenating things, and it doesn't make
        sense to concatenate something with a null. */

     if (null == res)
     {
       if (!nullsOk)
         throw StepExceptionBase("")
     }
     else
     {
       if ("\b" in res)
         throw StepExceptionBase("")
     }



     /*************************************************************************/
     /* If the result is non-null, we try expanding it again, and then also
        replace the special markers I use for escaped parens. */

     if (null != res)
     {
       res = expandReferencesTopLevel(res, nullsOk, errorStack)
       //$$$res = res!!.replace("JamieBra", "(").replace("JamieKet", ")")
       res = StepStringUtils.unmarkBalancedParens(res!!)
     }

     errorStack.pop()
     return res
  }

  /****************************************************************************/
  /* Applies any additional specialist processing required on certain types
     of value. */

  private fun specialistProcessing (value: String, fn: String): String
  {
    var revisedValue = value

    when (fn.lowercase())
    {
      "todate" ->
      {
        if (revisedValue.matches(".*([+-])\\d\\d:\\d\\d$".toRegex())) // Assume standard format but may need to get rid of daylight savings because LocalDateTime can't handle it.
          revisedValue = revisedValue.substring(0, revisedValue.length - 7)
        val ldt = LocalDateTime.parse(revisedValue)
        val cal = Calendar.getInstance()
        cal[ldt.year, ldt.monthValue - 1] = ldt.dayOfMonth
        revisedValue = SimpleDateFormat("yyyy-MM-dd").format(cal.time)
      }

      "totextdirection" ->
      {
        revisedValue = revisedValue.lowercase()
        revisedValue = if (revisedValue == "ltr") "LtoR" else "RtoL"
        if ("RtoL" == revisedValue) revisedValue = "BiDi" // Assume bidirectional on all RTL scripts.
      }
    } // when


    return revisedValue
  }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                           Book descriptors                             **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    fun clearVernacularBibleDetails () = m_BookDescriptors.clear()


    /****************************************************************************/
    /**
    * Returns a list of vernacular book descriptors, if these are known (an
    * empty list if not).  The list follows the ordering specified in the
    * metadata, so if that has indicated that books are required to be out of
    * order, that's what you'll get.
    *
    * @return Book descriptors.
    */

    fun getBookDescriptors (): List<VernacularBookDescriptor>
    {
      if (m_BookDescriptors.isNotEmpty()) return m_BookDescriptors

      get("stepBookList") // Dummy call which forces things to be populated from external metadata if available.
      if (m_BookDescriptors.isNotEmpty()) return m_BookDescriptors

      m_BookDescriptors = BibleBookNamesUsx.getBookDescriptors().toMutableList()
      return m_BookDescriptors
    }


    /****************************************************************************/
    /* Book details need to be of the form:

         #VernacularBookDetails usxAbbrev:=abbr:=abc;short=abcd;long=abcde

       where there is no _need_ to have more than one of the abbr/short/long.
    */

    fun processVernacularBibleDetails (theLine: String)
    {
        var line = theLine.replace("#VernacularBookDetails", "").trim()

        val bd = VernacularBookDescriptor("", "", "", "")

        val ix = line.indexOf(":")
        bd.ubsAbbreviation = line.substring(0, ix).trim()

        line = line.substring(ix + 2)
        val names = line.split(";")

        for (nameDetails in names)
        {
            val elts = nameDetails.split(":")
            val length = elts[0].trim().lowercase()
            val text = elts[1].trim()
            when (length)
            {
                "abbr"  -> bd.vernacularAbbreviation = text
                "short" -> bd.vernacularShort = text
                "long"  -> bd.vernacularLong = text
            }
        }

        m_BookDescriptors.add(bd)
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                          Miscellaneous gets                            **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * Returns a list of all the folders under the shared config folder which have
    * been accessed (with the exception of _Common_).  This list can be used to
    * make sure we include all relevant config data in the repository package.
    *
    * @return List of folders.
    */

    fun getSharedConfigFolderPathAccesses() = m_SharedConfigFolderPathAccesses


    /****************************************************************************/
    /**
    * Does what it says on the tin.  Intended presently only for use by the
    * various reference readers and writers.
    *
    * @param prefix The prefix for configuration parameters.
    */

    fun getValuesHavingPrefix (prefix: String): Map<String, String?>
    {
        val res: MutableMap<String, String?> = HashMap()
        m_Metadata.filterKeys { it.startsWith(prefix) }. forEach { res[it.key] = m_Metadata[it.key]!!.m_Value }
        return res
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                        USX-to-OSIS translations                        **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * Markers used to handle tag translations requiring special processing.
    */

    enum class TagAction { None, SuppressContent }


    /****************************************************************************/
    /**
    * Enables ConverterUsxToOsis to obtain details of USX-to-OSIS tag
    * translations.
    *
    * @param extendedUsxTagName Name of tag including leading '/' on closing
    *   tags, and including style name on para and char (para:q).
    *
    * @return Corresponding OSIS tag details, or null.
    */

    fun getUsxToOsisTagTranslation (extendedUsxTagName: String): Pair<String, TagAction>?
    {
      return m_UsxToOsisTagTranslationDetails[extendedUsxTagName]
    }


    /****************************************************************************/
    /* This takes the tag list used to reflect a USX start tag, and creates an
    equivalent list to represent the end tag.  I assume here that there is
    no non-tag text before the tag(s), after them, or between them. */

    private fun convertOsisStartToOsisEnd (osisStart: String): String
    {
        var res = ""

        val splitTags = osisStart.split("<")
        for (i in splitTags.size - 1 downTo 1) // 0'th entry will be empty or will contain a non-tag, and can therefore be ignored.
        {
            var s = splitTags[i]
            if (!s.endsWith("/>")) // Don't want to output a closing tag if the original was self-closing.
            {
                s = "/" + s.split(" ")[0]
                if (!s.endsWith(">")) s += ">"
                res += "<$s"
            }
        }

        return res
    }


    /****************************************************************************/
    /* Takes the saved tag translation rows and converts them to usable form. */

    private fun generateTagTranslationDetails ()
    {
      val forcedAssignments: MutableSet<String> = mutableSetOf()
       m_RawUsxToOsisTagTranslationLines.forEach { processConverterStepTagTranslation(forcedAssignments, it) }
    }


    /****************************************************************************/
    /* Converts the saved translation lines to the form needed for further
    processing. */

    private fun processConverterStepTagTranslation (forcedAssignments: MutableSet<String>, theLine: String)
    {
        /**************************************************************************/
        if (theLine.matches(Regex("(?i).*\\btbd\\b.*"))) return



        /**************************************************************************/
        val forced = "#=" in theLine
        var line = theLine.split(Regex("="), 2)[1]
        line = line.substring(line.indexOf("=") + 1).trim() // Get the RHS.
        line = expandReferences(line, false)!!.trim()
        val usx = line.split(" ")[0].substring(1).replace("Ͽ", "")



        /**************************************************************************/
        /* With forced assignments, the first one takes precedence, so if we already
           have a forced setting for this USX entry, we can ignore this one.

           If this is itself a forced setting, then we record the fact, in order
           that the check here works.

           But if we get past this point, then we want to take the assignment on
           board come what may. */

        if (usx in forcedAssignments)
          return
        else if (forced)
          forcedAssignments += usx



        /**************************************************************************/
        val regex = Regex("(?<key>\\w+)\\s*=\\s*(?<val>Ͼ.*?Ͽ)")
        val attributes: MutableMap<String, String> = HashMap()
        var matchResults = regex.find(line)
        while (null != matchResults)
        {
            val key = matchResults.groups["key"]!!.value
            var value = matchResults.groups["val"]!!.value
            value = value.replace("Ͼ", "").replace("Ͽ", "")
            attributes[key] = value
            matchResults = matchResults.next()
        }



        /**************************************************************************/
        /* If we have an action tag, it involves special processing.  At present
           the only special processing we support is to ditch a tag and all of its
           nested contents. */

        var tagAction: TagAction = TagAction.None
        when (if (attributes.containsKey("action")) attributes["action"] else "")
        {
            "suppressContent" -> tagAction = TagAction.SuppressContent
        }


        /**************************************************************************/
        /* If we have an OSIS tag, it represents both the start and end. */

        val osisEnd: String?
        var osisStart: String? = if (attributes.containsKey("osis")) attributes["osis"] else null
        if (null != osisStart)
        {
            osisEnd = convertOsisStartToOsisEnd(osisStart)
            m_UsxToOsisTagTranslationDetails[usx] = Pair(osisStart, tagAction)
            m_UsxToOsisTagTranslationDetails["/$usx"] = Pair(osisEnd, tagAction)
            return
        }


        /**************************************************************************/
        /* Otherwise, we assume that we have either or both of osisStart and
           osisEnd. */

        osisStart = if (attributes.containsKey("osisStart")) attributes["osisStart"]!! else ""
        osisEnd = if (attributes.containsKey("osisEnd")) attributes["osisEnd"]!! else ""
        m_UsxToOsisTagTranslationDetails[usx] = Pair(osisStart, tagAction)
        m_UsxToOsisTagTranslationDetails["/$usx"] = Pair(osisEnd, tagAction)
    }


    /****************************************************************************/
    /* Saves tag translation lines to a list for later processing.  This enables
       us to apply @(...) expansion.  I can't imagine that we'd ever want to, but
       I want things to be uniform. */

    private fun saveUsxToOsisTagTranslation (line: String)
    {
      m_RawUsxToOsisTagTranslationLines.add(line)
    }






    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                            OSIS support                                **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /** This gets rather complicated.  Originally I had hoped to be able to define
    *  this in the same was as all the other config information -- ie in a config
    *  file -- but there are just too many conditional inclusions etc for this
    *  to be really feasible.
    */

    fun makeBibleDescriptionAsItAppearsOnBibleList (bookNumbers: List<Int>)
    {
       /**********************************************************************/
       /* Allow the user to specify a value where it is necessary to force the
          issue (typically this will be where a module already exists and we
          want to retain its name). */

       if (null != ConfigData["stepBibleDescriptionAsItAppearsOnBiblePicker"])
         return



       /**********************************************************************/
        /* We want the English and vernacular titles, except where they are
           basically the same thing, in which case the vernacular is essentially
           irrelevant.  I further assume here that the vernacular title may not
           be available, but that the English title will always be available. */

        var englishTitle = get("stepBibleNameEnglish")!!
        var vernacularTitle = get("stepBibleNameVernacular")

        if (null != vernacularTitle)
        {
          val englishTitleLowerCase = englishTitle.lowercase()
          val vernacularTitleLowerCase = vernacularTitle.lowercase()

          if ("eng".equals(get("stepLanguageCode3Char"), ignoreCase = true) ||
              vernacularTitleLowerCase in englishTitleLowerCase ||
              englishTitleLowerCase in vernacularTitleLowerCase)
            vernacularTitle = null
        }



        /**********************************************************************/
        /* And the English and vernacular abbreviations, except, again, where
           they are essentially the same thing. */

        val englishAbbreviation = get("stepAbbreviationEnglish")!!
        var vernacularAbbreviation = get("stepAbbreviationVernacular")

        if (null != vernacularAbbreviation && englishAbbreviation.lowercase() == vernacularAbbreviation.lowercase())
          vernacularAbbreviation = null



         /**********************************************************************/
         englishTitle = "$englishTitle ($englishAbbreviation)"
         if (null != vernacularAbbreviation && null != vernacularTitle) vernacularTitle = "$vernacularTitle ($vernacularAbbreviation)"
         val titlePortion = listOfNotNull(englishTitle, vernacularTitle).joinToString(" / ")



        /**********************************************************************/
        var abbreviatedNameOfRightsHolder = get("stepTextOwnerOrganisationAbbreviatedName")
        if (null != abbreviatedNameOfRightsHolder && abbreviatedNameOfRightsHolder.lowercase() in titlePortion.lowercase()) abbreviatedNameOfRightsHolder = null



        /**********************************************************************/
        var officialYear = makeStepBibleDescriptionAsItAppearsOnBiblePicker_getOfficialYear()
        if (null != officialYear)
        {
           if (englishTitle.contains(officialYear))
             officialYear = null
           else if (null != vernacularTitle && vernacularTitle.contains(officialYear))
             officialYear = null
        }



        /**********************************************************************/
        var abbreviatedNameOfRightsHolderAndOfficialYearPortion: String? = listOfNotNull(abbreviatedNameOfRightsHolder, officialYear).joinToString(" ")



        /**********************************************************************/
        var coveragePortion: String? = makeStepBibleDescriptionAsItAppearsOnBiblePicker_getBiblePortion(bookNumbers)



        /**********************************************************************/
        var languagePortion: String? = makeStepBibleDescriptionAsItAppearsOnBiblePicker_getLanguage(englishTitle)



        /**********************************************************************/
        val languageCode = get("stepLanguageCode3Char")!!
        val countriesWhereUsedPortion =
          if (languageCode in "grc.hbo.cmn.deu.eng.fra.fre.ger.nld.por.spa.") // Don't give details for ancient languages, common European languages and a few others.
            null
          else
            "${IsoLanguageAndCountryCodes.getCountriesWhereUsed(languageCode)}."



        /**********************************************************************/
        //val moduleMonthYear = makeStepBibleDescriptionAsItAppearsOnBiblePicker_getModuleMonthYear().trim()


        /**********************************************************************/
        if (null != abbreviatedNameOfRightsHolderAndOfficialYearPortion && abbreviatedNameOfRightsHolderAndOfficialYearPortion.isBlank()) abbreviatedNameOfRightsHolderAndOfficialYearPortion= null
        if (null != coveragePortion && coveragePortion.isBlank()) coveragePortion= null
        if (null != languagePortion && languagePortion.isBlank()) languagePortion= null
        ConfigData["stepBibleDescriptionAsItAppearsOnBiblePicker"] =
          listOfNotNull(titlePortion, abbreviatedNameOfRightsHolderAndOfficialYearPortion, coveragePortion, languagePortion, countriesWhereUsedPortion).joinToString(" | ")
    }


    /****************************************************************************/
    /* Simplest if I just copy the relevant portion of the spec :-

     - The part of the Bible (OT+NT+Apoc) is only stated if it doesn’t contain
       OT+NT.  This seems to suggest that you don't mention the Apocrypha on
       a text which contains OT+NT even if it's present, and I suspect that's
       not what's meant.

     - If there are five books or fewer, they are listed (eg OT:Ps; Lam +NT).

     - If there are more than give books, but not the complete set, mark the
       text as eg 'OT incomplete +NT'.
    */

    private fun makeStepBibleDescriptionAsItAppearsOnBiblePicker_getBiblePortion (bookNumbers: List<Int>): String?
    {
        /************************************************************************/
        val C_MaxIndividualBooksToReport = 5
        val otBooks = bookNumbers.filter{ BibleAnatomy.isOt(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
        val ntBooks = bookNumbers.filter{ BibleAnatomy.isNt(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
        val dcBooks = bookNumbers.filter{ BibleAnatomy.isDc(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }



        /************************************************************************/
        var fullOt = otBooks.size == BibleAnatomy.getNumberOfBooksInOt()
        val fullNt = ntBooks.size == BibleAnatomy.getNumberOfBooksInNt()



        /************************************************************************/
        if (!fullOt && otBooks.isNotEmpty())
        {
            var nOtBooks = otBooks.size
            if (dcBooks.contains("Dag") && !otBooks.contains("Dan")) ++nOtBooks
            if (dcBooks.contains("Esg") && !otBooks.contains("Est")) ++nOtBooks
            fullOt = nOtBooks == BibleAnatomy.getNumberOfBooksInOt()
        }



        /************************************************************************/
        if (fullOt && fullNt) return if (dcBooks.isEmpty()) null else "+OT2"
        if (fullOt && ntBooks.isEmpty()) return if (dcBooks.isEmpty()) "OT only" else "OT+OT2 only"
        if (fullNt && otBooks.isEmpty()) return if (dcBooks.isEmpty()) "NT only" else "OT2+NT only"



        /************************************************************************/
        if (ntBooks.isEmpty() && otBooks.isEmpty()) return "OT2 only"



        /************************************************************************/
        val otPortion =
          if (fullOt)
              "OT "
          else if (otBooks.isEmpty())
              ""
          else if (otBooks.size > C_MaxIndividualBooksToReport)
               "OT (partial) "
          else
          {
              val fullBookList = otBooks.sortedBy { BibleBookNamesUsx.nameToNumber(it) }
              fullBookList.joinToString(", ") + " only "
          }



        /************************************************************************/
        val ntPortion =
          if (fullNt)
              "NT "
          else if (ntBooks.isEmpty())
              ""
          else if (ntBooks.size > C_MaxIndividualBooksToReport)
              "NT (partial) "
          else
          {
              val fullBookList = ntBooks.sortedBy { BibleBookNamesUsx.nameToNumber(it) }
              fullBookList.joinToString(", ") + " only "
          }



        /************************************************************************/
        val apocPortion = if (dcBooks.isEmpty()) "" else " OT2 "



        /************************************************************************/
        val haveNtPortion = ntPortion.isNotEmpty()
        val haveApocPortion = apocPortion.isNotEmpty()
        var res = otPortion + if (otPortion.isNotEmpty() && (haveNtPortion || haveApocPortion)) " + " else ""
        res += ntPortion + if (haveApocPortion) " + " else ""
        res += apocPortion

        if (res.startsWith("+ ")) res = res.substring(2)

        return res.ifEmpty { null }
    }


    /****************************************************************************/
    /* This is the year which relates to the publication itself, and there are
       various options for it, in descending order of priority :-

        - Most recent copyright year.
        - Publication year.
        - Date-stamp on the datafile.
        - Date the file was downloaded.

       It's difficult to know what to do with this, not least because everything
       seems to be a law unto itself.

       At present I'm dealing with Biblica texts from DBL.  For some of these we
       have been given specific copyright information as part of our agreement
       with DBL.  These do not, in general, identify the copyright year as such
       (ie as an entity in its own right), but they do usually have it within a
       larger string, and -- so long as all of them are like the one I'm looking
       at currently -- will have that preceded by a copyright symbol, so I can at
       least extract the year from the available text.

       Not all of the Biblica texts are like this, however, and where we have not
       been given specific instructions regarding what copyright information to
       use, I am forced back upon the DBL metadata.  Unfortunately, there does not
       seem to be a tag corresponding to the copyright year (not sure if the
       DBL metadata supports one, but even if it does, Biblica aren't using it).

       In the case of the Biblica files, there is freeform text under
       copyright/fullStatement/statementContent, and again it is possible to
       parse this -- although we have to accept that because this is freeform
       text, there is no guarantee that we will always be able to do so.

       If the copyright year cannot be extracted, the STEP spec calls for
       publication year to be used instead.  Assuming this refers to the year
       the translators published the text, rather than the year STEP did its
       stuff, again either DBL lacks a tag to identify this, or else Biblica are
       not using it.  There _is_ a tag 'dateCompleted', but I have no reason to
       believe that is meaningful.  In view of all this, I am not attempting to
       give a publication date.

       The next alternative, according to the STEP specification (above), is the
       date-stamp on the data file.  Again I'm not sure what this means; the only
       sense I can assign to it is the date on which the files were downloaded,
       and that's covered by the final option (below), and therefore does not
       actually constitute a separate option.

       The final alternative is the date the data was downloaded.  However, we
       seldom download stuff much in advance of generating a module for it
       (certainly not often in a different year), and we're giving the module-
       generation date later in the description string which the present method is
       being used to help assemble, and there seems little point in duplicating
       it.

       In summary, therefore, if I can parse and find an actual copyright year in
       the freeform copyright information, then you'll get it (although I'm not
       convinced that we've seen enough different ways of representing this for
       my processing in this respect to be comprehensive).  If I _can't_ get it by
       this means, then I don't bother to give a year at all. */

    private fun makeStepBibleDescriptionAsItAppearsOnBiblePicker_getOfficialYear (): String?
    {
        /************************************************************************/
        val mainPat = Regex("(?i)(&copy;|©|(copyright))\\W*(?<years>\\d{4}(\\W+\\d{4})*)") // &copy; or copyright symbol or the word 'copyright' followed by any number of blanks and four digits.
        val subPat = Regex("(?<year>\\d{4})$") // eg ', 2012' -- ie permits the copyright details to have more than one date, in which case we take the last.
        var res: String? = null



        /************************************************************************/
        for (key in listOf("stepAboutAsSupplied", "stepShortCopyright"))
        {
            var s = get(key) ?: continue
            var matchResult = mainPat.find(s)
            if (matchResult != null)
              s = matchResult.groups["years"]!!.value

            matchResult = subPat.find(s)
            if (null != matchResult)
            {
                res = matchResult.groups["year"]!!.value
                break
            }
        }



        /************************************************************************/
        return res?.trim()
    }


    /****************************************************************************/
    /* We want the language name only if it's not English, and is not already
    mentioned in the Bible name. */

    private fun makeStepBibleDescriptionAsItAppearsOnBiblePicker_getLanguage (bibleNameInEnglish: String): String?
    {
        val languageName = get("stepLanguageNameInEnglish")
        val languageNameLowerCase = languageName!!.lowercase()
        if ("english" == languageNameLowerCase) return null
        return if (bibleNameInEnglish.lowercase().contains(languageNameLowerCase)) null else " In $languageName "
    }


    /****************************************************************************/
    /* The spec says that the module date should be included only if we make
       changes to the text, for example pursuant to reversification.  However,
       given the potential need to restructure the text so that markup does not
       run across verse boundaries (to keep osis2mod happy), we almost always
       _will_ be making changes, so I always give the date.*/

    private fun makeStepBibleDescriptionAsItAppearsOnBiblePicker_getModuleMonthYear (): String
    {
        val s = DateTimeFormatter.ofPattern("MMM@yy").format(LocalDate.now())
        return s.replace("@", "'")
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /* Does what it says on the tin -- takes a string which may contain commas
       and (hopefully balanced) parentheses, and splits the string at commas
       outside of parens.  The individual elements are then trimmed. */

    private fun splitStringAtCommasOutsideOfParens (line: String): List<String>
    {
      var level = 0
      var modifiedString = ""
      for (c in line)
      {
        var newC = c
        when (c)
        {
          '(' -> ++level
          ')' -> --level
          ',' -> if (0 == level) newC = '\u0003'
        }
        modifiedString += newC
      }

      return modifiedString.split("\u0003").map { it.trim() }
    }


    /****************************************************************************/
    private fun tidyVal (vv: String): String
    {
        val v = vv.trim()
        if ("{empty}".equals(v, ignoreCase = true)) return ""
        return v.trim().replace(Regex("(?i)\\{space}"), " ")
    }


    /****************************************************************************/
    data class ParameterSetting (var m_Value: String?, var m_Force: Boolean)


    /****************************************************************************/
    private var m_BookDescriptors: MutableList<VernacularBookDescriptor> = ArrayList()
    private val m_CopyAsIsLines: MutableList<String> = ArrayList()
    private val m_EnglishDefinitions: MutableSet<String> = mutableSetOf()
    private var m_Initialised: Boolean = false
    private val m_Metadata = TreeMap<String, ParameterSetting?>(String.CASE_INSENSITIVE_ORDER)
    private val m_RawUsxToOsisTagTranslationLines: MutableList<String> = ArrayList()
    private val m_RegexesForForcedOsisPreprocessing: MutableList<Pair<Regex, String>> = mutableListOf()
    private val m_RegexesForGeneralInputPreprocessing: MutableList<Pair<Regex, String>> = mutableListOf()
    private val m_SharedConfigFolderPathAccesses: MutableSet<String> = mutableSetOf()
    private val m_UsxToOsisTagTranslationDetails: MutableMap<String, Pair<String, TagAction>> = TreeMap(String.CASE_INSENSITIVE_ORDER)


   /****************************************************************************/
   /* Used for debug purposes. */

    private object ConfigFilesStack
    {
      fun getSummary () = if (m_Summary.isEmpty()) "Calculated" else m_Summary

      fun pop ()
      {
        val x = m_Summary.split(" / ")
        m_Summary = x.subList(0, x.size - 1).joinToString(" / ")
      }

      fun push (path: String)
      {
        if (m_Summary.isNotEmpty()) m_Summary += " | "
        m_Summary += path
      }


      private var m_Summary = ""
    }


    /****************************************************************************/
    private fun getBookListAddBook (otBooks: MutableSet<String>, ntBooks: MutableSet<String>, dcBooks: MutableSet<String>, s: String)
    {
        val bookNo = BibleBookNamesUsx.nameToNumber(s)
        if (BibleAnatomy.isOt(bookNo))
            otBooks.add(s)
        else if (BibleAnatomy.isNt(bookNo))
            ntBooks.add(s)
        else
            dcBooks.add(s)
    }


    /****************************************************************************/
    private fun getBookList (otBooks: MutableSet<String>, ntBooks: MutableSet<String>, dcBooks: MutableSet<String>, filePath: String)
    {
      /************************************************************************/
      fun processLine (line : String): Boolean
      {
        if (!line.contains("code")) return false

        var s = line.split("code")[1]
        s = s.split(Regex("[\"']"))[1]
        s = s.split(Regex("[\"']"))[0].trim()


        // In theory s is already what we need.  But the conversion below
        // forces the abbreviation into canonical form (and will also check
        // that the abbreviation is one we know about).
        val bookNo = BibleBookNamesUsx.nameToNumber(s)
        if (!Dbg.wantToProcessBook(bookNo)) return false

        s = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
        getBookListAddBook(otBooks, ntBooks, dcBooks, s)

        return true // Finished with this file.
      }



      /************************************************************************/
      try
      {
        File(filePath).forEachLine { if (processLine(it)) throw StepExceptionBase("")  }
      }
      catch (_: Exception)
      {
      }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Calculated values                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Various methods which calculate config data on demand. */

  private val m_DataCalculators: Map<String, () -> String?> = mapOf(
    "stepAcknowledgmentOfDerivedWork" to
      { if (get("stepChangesMadeByUsInDerivedWork", "").isEmpty())
          ""
        else
          "<p>" + getInternal("stepChangesMadeByUsInDerivedWork", false)!! + " " + get("stepWordingForDerivedWorkStipulatedByTextSupplier", "")
      },

    "stepExtendedLanguageCode" to
      {
        val languageCode = get("stepLanguageCode2Char")!!
        val script = get("stepSuppliedScriptCode")!!
        val country = get("stepSuppliedCountryCode")!!
        var res = listOf(languageCode, script, country).joinToString("-")
        while (res.endsWith("-")) res = res.substring(0, res.length - 1)
        res
      },

    "stepForceVersePerLine" to { "false" },

    "stepJarVersion" to { MiscellaneousUtils.getJarVersion() },

    "stepLanguageNameInEnglish" to { IsoLanguageAndCountryCodes.getLanguageName(getInternal("stepLanguageCode3Char", false)!!) },

    "stepModuleCreationDate" to { SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(Date()) },

    "stepTextDirection" to { Unicode.getTextDirection(m_SampleText) }, // Need a string taken from a canonical portion of the scripture as input.

    "stepTextDirectionForSword" to
      {
        val x = (getInternal("stepTextDirection", true) ?: "LTR").uppercase()
        if ("LTR" == x || "LTOR" == x) "LtoR" else "RtoL"
      },

    "stepTextModifiedDate" to { SimpleDateFormat("dd-MMM-yyyy").format(Date()) }
  )


  /****************************************************************************/
  /* Called to check if a given configuration item is actually supplied by a
     calc_ function.  If it is, it carries out the calculation, and stores the
     result for future use.  Storing it means that it will not be subject to
     changes if the things upon which it depends change.  It's swings and
     roundabouts as to whether this is the right thing to do, but I think
     it's probably more comprehensible if it remains constant after first
     being accessed.

     Note that I don't set the 'force' flag when storing the calculated
     value.  Again a bit of a moot point, but I don't think I want to override
     any values which the user may have chosen to supply.  Not that we'll
     actually get as far as the present method anyway if we already have a
     value available for the selected parameter. */

  private fun getCalculatedValue (key: String): String?
  {
    //Dbg.d(key)
    val fn = m_DataCalculators[key] ?: return null
    val res = fn()
    if (null != res) put(key, res, false)
    return res
  }
}
