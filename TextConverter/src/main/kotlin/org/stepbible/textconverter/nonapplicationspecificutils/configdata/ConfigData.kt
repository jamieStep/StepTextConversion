/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata


import configDataParserBaseVisitor
import configDataParserLexer
import configDataParserParser

import org.stepbible.textconverter.applicationspecificutils.Digest
import org.stepbible.textconverter.applicationspecificutils.InternalOsisDataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.get
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.iso.IsoLanguageAndCountryCodes
import org.stepbible.textconverter.nonapplicationspecificutils.iso.Unicode
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils
import org.stepbible.textconverter.nonapplicationspecificutils.shared.FeatureIdentifier
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.*
import java.io.*
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNames
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt


/******************************************************************************/
/**
 * Reads, stores and makes available configuration data.
 *
 * Probably both you and I will come to hate me for this, but configuration
 * data is very complicated, and given that users need to know how to handle
 * it, and that I don't want to duplicate loads of data in two different
 * formats, I've removed all the head-of-class documentation from here and
 * put it into the user guide -- a Word doc which you can find in this JAR
 * file.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigData: ObjectInterface
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
  /**                             Command line                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   *  Saves the command line in case it's useful when rebuilding the module.
   *  Note that the result needs to be used with some care.  There is no
   *  platform-independent of getting at the raw command-line -- the program
   *  only ever gets to see the parsed arguments, and in these escapes etc
   *  will already have been processed.  I do my best to get back to the
   *  original here, but there's no absolute guarantee.
   *
   *  @param args Command-line arguments.
   */

  fun commandLineWas (args: Array<String>)
  {
    m_CommandLine = args.joinToString(" "){ if (it.startsWith("-")) it else "\"${it.replace("\"", "\\\"")}\"" }
  }


  /****************************************************************************/
  /**
  * Returns the command line.
  *
  * @return Command line.
  */

  fun getCommandLine () = m_CommandLine

  private var m_CommandLine = ""





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
     by an underscore.  This should include ...

     - 'public' if it is permissible to generate a public module from the
       text.

     - 'step' if it is permissible to generate a STEPBible module.

     - 'onlineOnly' if we do not have permission to make the module available
       offline.


     This suffix is not case-sensitive, so you are free, for instance, to use
     camelback notation to aid readability.

     Note that these are not mutually exclusive, and indeed you could have all
     three if you were permitted to make public and STEPBible modules from the
     text but could make things available only online (not that this is a very
     likely scenario).

     Note also that the settings are very likely to reflect copyright conditions
     -- for instance in general you are unlikely to be able to produce a public
     module from a copyright text.  However, I deliberately do not derive the
     settings from a knowledge of whether the text is copyright or not, because
     there _are_ special cases (for example we have been told that it is ok to
     make a public module for one particular copyright text we have been
     handling recently).

     A few examples:

       Text_eng_KJV_public: An English language text with the abbreviated name
         'KJV', which is to be turned into a publicly available module.  (By
         this I mean that we are free to generate a module which can then be
         offered in our repository for use even outside of STEP.)

       Text_ger_HFA or Text_ger_HFA_step: A German language text with the
         abbreviated name HFA which must give rise to a module which is used
         only within STEP.



     Language code
     -------------

     The language code should be the three character ISO code, all lowercase.
     Some languages have more than one ISO code, and here we normally have our
     own preference -- 'ger' rather than 'deu' for instance.  Note that where
     this is the case, our preference may not always reflect either the ISO
     preference or the code preferred by the translators.

     At present we have not given any consideration to how to handle things if
     it is ever necessary to have a country-specific language code (eg Latin
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

     Text_fra_BDS_step gives rise to a MODULE called FraBDS.  The language
     code is suppressed on English Bibles and on texts in the ancient
     languages.

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
    /* Things where we break our own rules. */

    val C_SpecialNaming =
      listOf(//"ArbKEH" to "NAV",
              "ChiCCB" to "CCB",
               "PesPCB" to "FCB",
                "PorNVI" to "PNVI",
                 "RonNTR" to "NTLR",
                  "RusNRT" to "NRT",
                   "SpaNVI" to "NVI",
                    "SwhNEN" to "Neno")



    /**************************************************************************/
    /* Parse the folder name and split out the main elements. */

    val parsedFolderName = "Text_(?<languageCode>...)_(?<abbreviation>[^_]+)(_(?<rest>.*)?)?".toRegex().matchEntire(FileLocations.getTextRootFolderName())!!
    var abbreviatedName = parsedFolderName.groups["abbreviation"]!!.value
    val rest = parsedFolderName.groups["rest"]?.value ?: ""



    /**************************************************************************/
    /* Cater for the possibility that we may have been given the 2-character
       code rather than the 3-character code in error; and then get the actual
       2-character and 3-character codes. */

    var languageCode = IsoLanguageAndCountryCodes.get3CharacterIsoCode(parsedFolderName.groups["languageCode"]!!.value)
    delete("calcLanguageCode3Char"); put("calcLanguageCode3Char", languageCode, force = true)
    delete("calcLanguageCode2Char"); put("calcLanguageCode2Char", IsoLanguageAndCountryCodes.get2CharacterIsoCode(languageCode).ifEmpty { languageCode }, force = true)



    /**************************************************************************/
    /* We may now have a legacy suffix (eg _th) AND an operational suffix (eg
       _public), or we may have just an operational suffix.  Split them out. */

    val suffixes = rest.split("_")
    var legacySuffix = ""
    var operationalSuffix = suffixes[0]

    if (2 == suffixes.size)
    {
      legacySuffix = suffixes[0]
      operationalSuffix = suffixes[1]
    }

    operationalSuffix = operationalSuffix.trim().lowercase()
    if (operationalSuffix.isEmpty()) operationalSuffix = "step" // For safety's sake, assume STEPBible only where no explicit indication is given.



    /**************************************************************************/
    /* 2026-03-12: Significant changes ...

       Henceforth, it is our intention _always_ to create a STEPBible version
       of each module.  If we are permitted to make a text publicly available,
       we will also create a public version.  But we will never create just a
       public version of a module.

       Or at least, that's the intent.  Because a single run of the converter
       creates only a single variant of a module, it is, in fact, perfectly
       possible to create just a public version of a module if we forget also
       to create a STEPBible version, but the aim is not to get into that
       position.

       The revised mechanics and module naming is as follows (which I _think_
       should be backwards compatible with existing folders):

       * The name of the text's root folder will indicate what kind of module(s)
         can be created.  If it ends with 'public', 'publicStep' or 'stepPublic',
         (case-insensitive) both flavours are to be created.  If it ends with
         'step' or has no suffix, only a STEPBible version is permitted.

       * Module names are based upon a combination of the 3-character language
         code in canonical form (uppercase, lowercase, lowercase) and the
         abbreviation for the text (vernacular abbreviation if that uses Roman
         characters, otherwise the English abbreviation).

       * On public modules, the format is <languageCode>_<abbreviation> (note
         the underscore between the two parts).

       * On STEPBible modules, the basic format is <languageCode><abbreviation>
         (without the intervening underscore).

       * On STEPBible modules, the language code is omitted if its canonical
         form would be Eng (English), Grc (koine Greek) or Hbo (ancient
         Hebrew).  (Note that the language code is _never_ suppressed on
         public modules.)

       Note that if both STEPBible and Public are permitted, the command line
       must give an indication of which of the two a given run is to generate.

       And whether we're permitted to build both or only one, we set the
       internal representation of this command line parameter to canonical
       (ie lower case) form.

       And finally, we check to see if this is online only, and if so (and if
       we have not been given a forcible assignment)*/

    val mayBePublic = "public" in operationalSuffix
    val mayBeStep   = true // Assume we can always generate a STEPBible version.  (This is now redundant, because we _always_ expect to generate this version, but I'll retain this as a variable here in case we change our minds again.)
    val optionFromCommandLine = CommandLineProcessor["targetAudience"]?.lowercase()

    val targetAudience: String
    if (mayBeStep && mayBePublic)
    {
       if (null == optionFromCommandLine) throw StepExceptionSilentCommandLineIssue("Could be public or STEP-only run.  Need targetAudience on command line to indicate which.")
       targetAudience = optionFromCommandLine
    }

    else if (mayBeStep)
    {
      targetAudience = "step"
      if (null != optionFromCommandLine && "step" != optionFromCommandLine)
        throw StepExceptionWithStackTraceAbandonRun("Folder name implies this is a STEP-only build, but targetAudience on the command line says otherwise.")
    }

    else // mayBePublic
    {
      targetAudience = "public"
      if (null != optionFromCommandLine && "public" != optionFromCommandLine)
        throw StepExceptionWithStackTraceAbandonRun("Folder name implies this is a public build, but targetAudience on the command line says otherwise.")
    }


    deleteAndPut("stepTargetAudience", targetAudience, true)

    if (null == ConfigData["calcOnlineUsageOnly"])
      deleteAndPut("calcOnlineUsageOnly", if ("online" in operationalSuffix) "Yes" else "No", true)



    /**************************************************************************/
    /* Get things into the format we require for file-naming etc. */
    
    if (legacySuffix.isNotEmpty()) legacySuffix = "_$legacySuffix"
    languageCode = StepStringUtils.sentenceCaseFirstLetter(languageCode)



    /**************************************************************************/
    /* See if it's one of the abbreviations which we got wrong in the past, but
       have to maintain in their wrong form for backwards compatibility. */
       
    val revisedAbbreviation = C_SpecialNaming.find { it.first == languageCode + abbreviatedName } ?.second
    if (null != revisedAbbreviation)
      abbreviatedName = revisedAbbreviation



    /**************************************************************************/
    /* For public modules, we always have the language code, an underscore,
       the abbreviated name and any legacy suffix.  For STEP modules, we drop
       the language code and we never have the underscore.  The existence of
       the underscore is a subtle way by which we can distinguish public and
       STEP modules. */
       
    val moduleName: String =
      if ("public" == targetAudience)
        languageCode + "_" + abbreviatedName + legacySuffix
      else
      {
        if (languageCode in listOf("Eng", "Grc", "Hbo"))
          languageCode = ""

        languageCode + abbreviatedName + legacySuffix
      }



    /**************************************************************************/
    delete("calcModuleName"); put("calcModuleName", moduleName, force = true)
    delete("stepAbbreviationEnglishAsSupplied"); put("stepAbbreviationEnglishAsSupplied", abbreviatedName, force = true)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Data-load                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Data-load _must_ occur right at the start of processing, before any thought
     of running things in parallel, so there is no need for this portion to be
     thread-safe. */

  /****************************************************************************/
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
   */

  fun load ()
  {
    /**************************************************************************/
    if (m_Initialised) return // Guard against multiple initialisation.
    m_Initialised = true




    /**************************************************************************/
    /* There are various miscellaneous items which have to be initialised
       early on.  The order below is crucial. */

    loadSettingsFromEnvironmentVariable()
    CommandLineProcessor.copyCommandLineOptionsToConfigData()
    FileLocations.setRootFolderDetails()
    extractDataFromRootFolderName()
    Rpt.report(level = 0, "Loading configuration data.")



    /**************************************************************************/
    /* Pick up initial config from either step.conf or step.xlsx, whichever is
       available. */

    val configTextFilePath = FileLocations.getConfigTextFilePath()
    val configSpreadsheetFilePath = FileLocations.getConfigSpreadsheetFilePath()

    //legacyHandler(configTextFilePath)

    if (null != configTextFilePath && null != configSpreadsheetFilePath && File(configTextFilePath).exists() && File(configSpreadsheetFilePath).exists())
      throw StepExceptionWithoutStackTraceAbandonRun("You must have a step*.xlsx file in the Metadata folder, but you must not have a step.conf file as well.")
    else if (null == configSpreadsheetFilePath || !File(configSpreadsheetFilePath).exists())
      throw StepExceptionWithoutStackTraceAbandonRun("You must have a step*.xlsx file in the Metadata folder.")

    load(configSpreadsheetFilePath, false)



    /**************************************************************************/
    processAutoLoads(FileLocations.getTextMetadataFolderName()) // Any .conf files co-located with step*.xlsx.
    processAutoLoads(FileLocations.getSharedCommonAutoloadFolderPath()) // Anything in the _Common_ folder in the shared configuration area.
    loadDone()
  }


  /****************************************************************************/
  private fun canonicaliseConfiguration ()
  {
    /**************************************************************************/
    fun forceLc (parameterName: String)
    {
      val x = m_Metadata[parameterName]!!
      delete(parameterName)
      m_Metadata[parameterName] = ParameterSetting(x.m_Value!!.lowercase(), x.m_Force, x.m_Resolved)
    }



    /**************************************************************************/
    put("calcCopyrightOrOpenAccess", if (getAsBoolean("stepIsCopyrightText")) "copyright" else "openAccess", force = true)
  }


  /****************************************************************************/
  /* Legacy issues.

     At one stage I had no notion of building more than one module from a
     given text, and therefore one set of history lines sufficed.  Now we
     may build both a STEPBible version and a public version off the same text
     where the licensing conditions permit.

     Also, I was storing the history information in the step.conf file.
     However at the time of writing we may be moving away from that, with a
     view to taking config information from a spreadsheet -- and once the
     spreadsheet becomes the long-term repository of config information, it
     becomes more difficult to update it with history information.

     The purpose of this present method is to determine where we have legacy
     data (which basically will be the case if we don't have history.conf in
     the Metadata folder).

     Where we have this legacy situation, it moves the history information
     across to history.conf and also handles the STEPBible / public
     duplication if necessary.
  */

  private fun legacyHandler (configTextFilePath: String?)
  {
    /**************************************************************************/
    /* I assume that if we already have a history.conf, there is nothing to
       do.  When looking for an existing history file, I'm content to trawl
       various folders for it.  If not found, I use the forced path, which
       places it in the metadata folder. */

    var historyFilePath = FileLocations.getExistingHistoryFilePath()
    if (null != historyFilePath && File(historyFilePath).exists())
      return

    historyFilePath = FileLocations.getForcedHistoryFilePath()



    /**************************************************************************/
    /* If we _don't_ have history.conf, I assume that we're definitely going to
       have to create one, and the one I create has some standard comments at
       the top.  If I have a step.conf as well, then I move any existing data
       across to the history file, and leave an explanation in step.conf.
       Beyond this, I retain step.conf as-is, because there is no guarantee that
       we have a spreadsheet version with which to replace it. */

    /**************************************************************************/
    val revised: MutableList<String> = mutableListOf()
    val publicHistoryLines: MutableList<String> = mutableListOf()
    val stepHistoryLines: MutableList<String> = mutableListOf()



    /**************************************************************************/
    if (null != configTextFilePath)
    {
      File(configTextFilePath).bufferedReader().lines().forEach {
        if (!it.startsWith("History"))
        {
          revised.add(it)
          return@forEach
        }

        if ("History_step_" in it)
          stepHistoryLines.add(it)
        else if ("History_public_" in it)
          publicHistoryLines.add(it)
        else
        {
          stepHistoryLines.add(it.replace("History_", "History_step_"))
          publicHistoryLines.add(it.replace("History_", "History_public_"))
        }
      } // forEach



      /************************************************************************/
      /* If step.conf yielded any history lines, update step.conf to say that the
         history has been moved to history.conf. */

      if (stepHistoryLines.isNotEmpty() || publicHistoryLines.isNotEmpty())
      {
        if (revised.isNotEmpty())
        {
          revised.add(""); revised.add(""); revised.add("")
        }

        revised.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        revised.add("#!")
        revised.add("#! Any history lines have now been moved to ${FileLocations.getHistoryFileName()}.")
        revised.add("#!")
        revised.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n")

        val writer = File(configTextFilePath).bufferedWriter()
        revised.forEach { writer.appendLine(it) }
        writer.close()
      }
    } // lines forEach



    /**************************************************************************/
    /* Write a header and any history lines to history.conf. */

    val writer = File(historyFilePath).bufferedWriter()
    val data: MutableList<String> = mutableListOf()
    data.clear()
    data.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    data.add("#!")
    data.add("#! History lines.  There may a set for STEPBible; there may be a set for public;")
    data.add("#! or there may be both.")
    data.add("#!")
    data.add("#! You need to retain this file so that we can keep a full change history.  You")
    data.add("#! can make changes if you wish, but you should do so with care.")
    data.add("#!")
    data.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    data.add("")
    data.addAll(stepHistoryLines)
    data.addAll(publicHistoryLines)
    data.forEach { writer.appendLine(it) }
    writer.close()
  }


  /****************************************************************************/
  /* Need this as a separate function because $include's involve recursive
     calls. */

  private val m_LoadedConfigFiles: MutableSet<String> = mutableSetOf()

  private fun load (configFilePath: String, okIfNotExists: Boolean)
  {
    /**************************************************************************/
    //Dbg.dCont(configFilePath, "DBL.conf")



    /**************************************************************************/
    val expandedFilePath = FileLocations.getConfigFileInputPath(configFilePath, okIfNotExists) ?: return
    if (expandedFilePath in m_LoadedConfigFiles)
      return

    m_LoadedConfigFiles.add(expandedFilePath)
    Rpt.report(level = 1, "Loading configuration data from $expandedFilePath.")



    /**************************************************************************/
    val takingDataFromSpreadsheet = expandedFilePath.endsWith(".xlsx")
    val lines: List<String>

    if (takingDataFromSpreadsheet)
    {
      val requiredStepXlsxTemplateVersion = 3.0
      val (actualStepXlsxTemplateVersion, theLines) = ConfigDataExcelReader.process()
      if (actualStepXlsxTemplateVersion < requiredStepXlsxTemplateVersion)
        throw StepExceptionWithoutStackTraceAbandonRun("I'm sorry about this, but this step.xlsx file is based on an old version of the template.  You'll need to take a copy of the current version of the template (which you can find in the JAR file), and transfer the data to that.")
      lines = theLines
    }
    else
      lines = ConfigArchiver.getDataFrom(expandedFilePath, okIfNotExists) ?: return



    /**************************************************************************/
    for (x in lines)
    {
      //Dbg.d(x.contains("include", ignoreCase = true) && x.contains("choose", ignoreCase = true))
      val line = x.trim().replace("\$home", System.getProperty("user.home"))
      if (processConfigLine(line, expandedFilePath)) continue // Common processing for 'simple' lines -- shared with the method which extracts settings from an environment variable.
      throw StepExceptionWithStackTraceAbandonRun("Couldn't process config line: $line")
    } // for

    ConfigFilesStack.pop()


    /**************************************************************************/
    // Dbg.d("Finished loading config file: " + configFilePath)
  }


  /****************************************************************************/
  /**
   * Called when all defaults and metadata have been handled.
   *
   * The method also checks that mandatory parameters have been supplied, and
   * sets to an empty string any optional parameters which have not been
   * supplied.
   *
   * If the configuration data implies that we may be picking up information
   * from external data files (eg those supplied by DBL), we also arrange to
   * pick up the details which indicate how that information can be obtained.
   */

  private fun loadDone ()
  {
    /**************************************************************************/
    canonicaliseConfiguration()



    /**************************************************************************/
    /* See we have if externally-supplied metadata or licence information. */

    val externalMetadataFormat = get("calcSelectedExternalMetadataFormat")
    if (null != externalMetadataFormat)
    {
      var x = get("calcSelectedExternalLicenceFileName")
      val licenceFileExists = if (null == x) false else null != FileLocations.getConfigFileInputPath(x, okIfNotExists = true)

      x = get("calcSelectedExternalMetadataFileName")
      val metadataFileExists = if (null == x) false else null != FileLocations.getConfigFileInputPath(x, okIfNotExists = true)

      if (licenceFileExists || metadataFileExists)
      {
        load(FileLocations.getPrefixForExternalDataInterface() + externalMetadataFormat + ".conf", true)
        val externalInterface = MiscellaneousUtils.createInstanceByClassName("org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigDataExternalFileInterface$externalMetadataFormat") as ConfigDataExternalFileInterfaceXml
        externalInterface.initialise()
        m_ExternalDataHandler = externalInterface
      }
    }



    /**************************************************************************/
    /* See if we have standard files describing the text owner or (if there is
       one) the repository organisation. */

    val ourIdentifierForTextOwner = get("stepOurInternalIdentifierForTextOwner")
    val ourIdentifierForRepositoryOrganisation = get("stepOurInternalIdentifierForRepositoryOrganisation")

    if (null != ourIdentifierForTextOwner)
      load(FileLocations.getPrefixForTextOwnerFiles() + ourIdentifierForTextOwner + ".conf", true)

    if (null != ourIdentifierForRepositoryOrganisation)
        load(FileLocations.getPrefixForRepositoryOrganisationFiles() + ourIdentifierForRepositoryOrganisation + ".conf", true)



    /**************************************************************************/
    /* Earlier legacy processing will have ensured that any history
       information is now in history.conf, and we need to ensure we load those
       details too. */

    load(FileLocations.getExistingHistoryFilePath()!!, false)



    /**************************************************************************/
    m_LoadedConfigFiles.forEach {
      Logger.info("Loaded config from ${(m_LoadedConfigFiles.size).toString().padStart(2, ' ')}) $it.")
    }



    /**************************************************************************/
    processRelatedSettings()
    generateTagTranslationDetails() // Convert any saved tag translation details to usable form.
    BibleBookNames.init()
    Dbg.setBooksToBeProcessed()
  }


  /**************************************************************************/
  /* We need to read the environment variable early because some settings are
     needed before anything else at all will run.

     Strictly, we need only those specific settings, but I don't think there's
     any harm in loading the lot -- the ones which matter I set as force,
     and the ones which don't as set as not forced.

     The format is:

       setting;setting;setting; ...

     where individual settings look as they would in a config file -- key=val.
     If you need a semicolon within a setting, escape it using \;.  If you
     need a backslash, escape it as \\.

     Clearly you're not going to want to store too many settings this way, but
     there may be things -- such as the location of osis2mod -- which is more
     easily handled like this, rather than storing it in config files. */

  private fun loadSettingsFromEnvironmentVariable ()
  {
    /**************************************************************************/
    /* Parse the environment variable into its elements. */

    var environmentVariable = System.getenv("StepTextConverterParameters") ?: throw StepExceptionWithoutStackTraceAbandonRun("Environment variable StepTextConverterParameters not defined.")
    environmentVariable = environmentVariable.replace("\\\\", "\u0001").replace("\\;", "\u0002")

    val chunks = environmentVariable.split(";").map { it.trim() }
    val chunksMap: Map<String, String> = chunks.associate { elt ->
      val (key, value) = elt.split("#?=".toRegex(), limit = 2)
      key.trim() to value.trim().replace("\u0001", "\\").replace("\u0002", ";")
    }



    /**************************************************************************/
    /* Handle the vital settings -- the ones which are needed early.  Note that
       we can't use put() to store these because that function also validates
       its input, and it needs the settings which these supply for the
       purpose. */

    val C_EarlyLoad = listOf("stepTextConverterOverallDataRoot", "stepTextConverterSharedConfigurationDataRoot", "stepTemporaryInvestigationsFolderPath", "stepOsis2modFilePath")

    for (key in C_EarlyLoad)
      m_Metadata[key] = ParameterSetting(chunksMap[key]!!, m_Force = true, m_Resolved = true) // Can't use put(...) because that requires a certain amount of config already to have been loaded.  So I have to do the essentials of what put() does.



    /**************************************************************************/
    /* Check if the environment variable contains only the things we are
       content to have there. */

    var allSettingsOk = true
    chunksMap.keys.forEach { key ->
      if (key !in C_EarlyLoad)
        allSettingsOk = false
    }

    if (!allSettingsOk)
      throw StepExceptionWithoutStackTraceAbandonRun("The StepTextConverterParameters environment variable is limited to the following path variables: ${C_EarlyLoad.joinToString(", ")}.")
  }



  /****************************************************************************/
  /**
   * This makes it possible to generate pseudo config lines internally and then
   * process them.
   *
   * @param configLine Line to be processing.
   * @param origin Origin of data for debug purposes.
   */

  fun loadFromInternalSetting (configLine: String, origin: String)
  {
    ConfigFilesStack.push(origin)
    if (!processConfigLine(configLine, ""))
      throw StepExceptionWithStackTraceAbandonRun("Couldn't parse setting : $configLine")
    ConfigFilesStack.pop()
  }


  /****************************************************************************/
  /* Parses a line so as to determine the key and value, and stores them.  Note
     that we store this as raw data -- we don't expand out parameter references
     at this point.  That occurs the first time a parameter is read

     Jun 2025: We are now hoping to take config details from a very rudimentary
     spreadsheet.  In particular the aim is that people should be able to cut
     and paste from that spreadsheet straight into a step.conf file.  More than
     this, ideally we want this to work regardless of whether they are using
     Excel, Google Sheets or LibreOffice Calc.  This means we don't want any
     processing or formulae in the file.

     Now the file contains a _lot_ of potential settings, and on any given run
     probably many of them will be empty simply because the user is happy to
     rely upon the defaults.

     Previously an empty reference (ie just x= or x#= with no right hand side)
     would have established an empty value for the parameter.  I no longer want
     that -- if the right-hand side is empty, I want to ignore the setting.
     (Unfortunately, this change may not be backwards compatible.)
   */

  private fun loadParameterSetting (line: String)
  {
    //Dbg.d(line)
    val force = line.contains("#=")
    val parts = line.split(Regex(if (force) "\\#\\=" else "\\="), 2)
    if (parts[1].trim().isNotEmpty()) // Jun 2025 -- see comment above.
      put(parts[0].trim(), parts[1].trim(), force)
  }


  /****************************************************************************/
  private fun processAutoLoads (folderPath: String)
  {
    val folderPath = File(folderPath)
    folderPath.walkTopDown()
      .filter { it.isFile && it.extension == "conf"}
      .sortedBy { it.name }
      .forEach { load(it.toString(), false) }
  }


  /****************************************************************************/
  /* This caters for that subset of configuration lines which can reasonably
     turn up both in config files and in the converter's environment
     variable. */

  private fun processConfigLine (xDirective: String, callerFilePath: String): Boolean
  {
    /**************************************************************************/
    if (xDirective.startsWith("abort!!!", ignoreCase = true))
      throw StepExceptionWithoutStackTraceAbandonRun("$callerFilePath is marked 'ABORT' -- ${xDirective.replace("abort!!!", "", ignoreCase = true).trim()}\nRemove the ABORT line once you have addressed any issues.")



    /**************************************************************************/
    /* Jun 2025: We are now taking inputs generated from a spreadsheet.  In
       order to allow the user to include ad hoc configuration statements in
       that input (ie statements which I don't cater for by way of explicit
       pre-defined assignments), I allow for a few fields with names starting
       '%'.  '%comment' is treated as a comment.  With anything else, I drop
       the '%...#=' bit, so that I process only the rest of the string. */

    if (xDirective.startsWith("%comment", ignoreCase = true))
      return true

    var directive = xDirective

    if (directive.startsWith("%"))
      directive = let {
        val ix = directive.indexOf("#=") + 2
        directive.substring(ix).trim()
      }



    /**************************************************************************/
    if (directive.isEmpty())
      return true



    /**************************************************************************/
    if (directive.startsWith("\$include"))
    {
      var newFilePath = directive.replace("${'$'}includeIfExists", "")
      newFilePath = newFilePath.replace("${'$'}include", "").trim()
      newFilePath = expandConfigData(newFilePath)!!
      load(newFilePath, directive.contains("exist", ignoreCase = true))
      return true
    }



    /**************************************************************************/
    if (directive.matches(Regex("(?i)^#vernacularbookdetails.*")))
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
    if (directive.matches(Regex("(?i)stepPreprocessingRegexWhenNotStartingFromOsis.*")))
    {
      processRegexLine(m_RegexesForGeneralInputPreprocessing, directive)
      return true
    }



    /**************************************************************************/
    if (directive.matches(Regex("(?i)stepPreprocessingRegexWhenStartingFromOsis.*")))
    {
      processRegexLine(m_RegexesForForcedOsisPreprocessing, directive)
      return true
    }



    /**************************************************************************/
    if (directive.contains("=")) // I think this should account for all remaining lines.
    {
      loadParameterSetting(directive)
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
  /* Some parameters are often derived from others, but in ways which can't be
     legislated for directly in the config data itself. */

  private fun processRelatedSettings ()
  {
    /**************************************************************************/
    /* Some parameters -- notably Bible names and abbreviations -- come in
       pairs, 'AsSupplied' and 'Canonical'.  If both are provided, then we use
       both.  If only the AsSupplied version is supplied, we take that as
       serving also as the Canonical. */

    m_Metadata.keys.filter { it.matches(".*stepBible(Name|Abbrev).*AsSupplied$".toRegex()) } .forEach {
      val canonicalKey = it.replace("AsSupplied", "CanonicalForBibleChooser")
      if (canonicalKey !in m_Metadata)
        put(canonicalKey, getInternal(it, false)!!, true)
    }



    /**************************************************************************/
    val isCopyrightText = getAsBoolean("stepIsCopyrightText", "yes") // Default to text being copyright -- that's safer.
    if (null == ConfigData["stepIsOkToAddFootnotes"]) // Unless we've specifically been told we can generate footnotes, derive the setting from the copyright setting.
      put("stepIsOkToAddFootnotes", if (isCopyrightText) "no" else "yes", force = true)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             get, put, etc                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

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

  @Synchronized fun checkIfIsParameterKey (purportedKey: String) = m_Metadata[purportedKey]


  /****************************************************************************/
  /**
   * Deletes a given key/value pair.  Intended mainly for use during command
   * line processing, where I need to be able to force things to their default
   * values, but then override these forced values with new forced values for
   * things which are not being defaulted.
   *
   * @param key
   */

  @Synchronized fun delete (key: String)
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

  @Synchronized fun deleteAndPut (key: String, theValue: String, force: Boolean)
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
   * Obtains the value associated with a given key, or throws an exception if
   * the key has no associated value.  The value is assumed to be a string
   * representation of a Boolean -- Y(es) or T(rue) or anything else.  Returns
   * true if the value starts with Y or T (not case-sensitive).
   *
   * @param key
   * @return True or false.
   */

  fun getAsBoolean (key: String, dflt: Boolean = false): Boolean
  {
    var s = get(key) ?: return dflt
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
   * The configuration data may include lines which are simply to be copied
   * as-is to the Sword configuration file.  This returns that list.
   *
   * @return As-is lines.
   */

  @Synchronized fun getCopyAsIsLines (): List<String> = m_CopyAsIsLines



  /****************************************************************************/
  /**
   *  Returns all of the keys from the metadata.
   *
   *  @return Keys.
   */

  @Synchronized fun getKeys () : Set<String> = m_Metadata.keys


  /****************************************************************************/
  /**
   *  Returns all of the keys from the metadata related to regex preprocessing
   *  for this particular flavour of run.
   *
   *  @return Keys.
   */

  fun getPreprocessingRegexes () =
    if ("osis" == ConfigData["calcOriginData"])
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
    if ("osis" == ConfigData["calcOriginData"])
      get("stepPreprocessingXsltStylesheetWhenStartingFromOsis")
    else
      get("stepPreprocessingXsltStylesheetWhenNotStartingFromOsis")


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

  @Synchronized fun put (key: String, theValue: String, force: Boolean)
  {
    /************************************************************************/
//    Dbg.d(key, "stepTextModifiedDate")



    /************************************************************************/
    ConfigDataSupport.validateParameter(key, "put")



    /************************************************************************/
    /* Dummy placeholder -- parameters may be included in files for reference
       purposes, but given the value '%%%UNDEFINED', in which case it is as
       though they had not been mentioned at all. */
         
    if ("%%%UNDEFINED" == theValue)
      return



    /************************************************************************/
    /* If this is a 'force' setting and we already have a force setting, we
       retain the existing one.  Otherwise we will definitely want to process
       this current call. */

    if (force)
    {
      if (key in m_Metadata && m_Metadata[key]!!.m_Force)
      {
        ConfigDataSupport.reportSet(key, theValue, ConfigFilesStack.getSummary(), "Skipped because forced value already in effect")
        return
      }
    }



    /************************************************************************/
    /* It's not a force setting.  We want to action this present call only
       if either a) we don't already have a setting at all; or b) the
       existing setting is not a force setting. */

    else
    {
      val tmp = m_Metadata[key]  // If we're not forcing, it's ok to store the new data if either there's no existing entry, or the existing one is not marked force.
      val processThisCall = null == tmp || !tmp.m_Force
      if (!processThisCall)
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
    m_Metadata[key] = ParameterSetting(value, m_Force = force, m_Resolved = false) // Mark as not resolved, just in case the value requires $ processing.
  }


  /****************************************************************************/
  /**
   * Replaces any existing value.
   *
   * @param key Key.
   * @param value Associated value.
   * @param force If true, later calls for this same key are ignored.
   */

  @Synchronized fun replace (key: String, value: String, force: Boolean = false)
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




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               getInternal                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Until recently I've been trying to parse configuration data myself (data
     which may contain $myVar references, $fn(...) function calls, etc).  This
     was getting to be too difficult and I was having to make too many special
     case functions.  I have therefore probably gone way over the top, in that
     I have moved to using Antlr to generate a fully-fledged parser.  The Antlr
     grammar is in main/antlr/configDataParser.g4.  From this, Antlr auto
     generates a number of classes to handle lexical and syntax analysis, which
     are used here.

     If you make any changes to the grammar you need to run:

       ./gradlew clean generateGrammarSource build

     in the IDEA command window (the 'clean' is optional, but useful if things
     have been going wrong).
  */

  /****************************************************************************/
  /* I've hived this off to a separate section because there's rather a lot of
     it.  The task of the functions here is to take configuration items and
     expand out function calls and parameter references.

     Formats are as follows:

       $myVar -- A reference to myVar.

       $myFn($a, $b, "...", $c) -- An invocation of myFn, passing the given
         arguments.

       Variable references are assumed to run from the $-sign through any
       subsequent word characters (of which there must be at least one).
       If you need a variable name to be followed immediately by more word
       characters, you can end the name with $$.

       Printer's double quotes are replaced by straight quotes.

       I make the simplifying assumption that $-signs will never be included
       as plain text -- ie that they will always indicate function invocation
       or parameter references.
  */

  /****************************************************************************/
  /* Expands a string potentially containing $-references etc. */

  @Synchronized fun expandConfigData (theData: String): String?
  {
    if (theData.isBlank())
      return ""

    if (theData.trim().startsWith("#")) // No point in attempting to parse comments.
      return theData

    val inputStream = CharStreams.fromString(theData)
    val lexer = configDataParserLexer(inputStream)
    val tokens = CommonTokenStream(lexer)

//    let {
//      tokens.fill()
//      tokens.tokens.forEach { println("${it.text} -> ${lexer.vocabulary.getSymbolicName(it.type)}") }
//    }

    val parser = configDataParserParser(tokens)

    parser.removeErrorListeners()
    parser.addErrorListener(VerboseErrorListener(theData))

    val tree = parser.file()
    val visitor = ParseTreeVisitor()
    val thunk = visitor.visit(tree)
    val res = thunk()
    return res
  }


  /****************************************************************************/
  /* This parses potential values and applies and processing implied by the
     content (eg expanding variable references etc). */

  @Synchronized fun getInternal (key: String, nullsOk: Boolean): String?
  {
    /**************************************************************************/
//    Dbg.d("swordTextOwnerOrganisationFullName", key)
    ConfigDataSupport.validateParameter(key, "get")



    /**************************************************************************/
    /* Get existing data and deal with nulls. */

    var existingValue = m_Metadata[key]
    if (null == existingValue) // See if we can calculate a value.
    {
      val fnForCalculatingValue = m_DataCalculators[key]
      if (null != fnForCalculatingValue)
      {
        existingValue = ParameterSetting(fnForCalculatingValue(), m_Force = true, m_Resolved = true)
        m_Metadata[key] = existingValue
      }
    }

    if (existingValue?.m_Value == null) // Still null?
    {
      if (!nullsOk)
        throw StepExceptionWithoutStackTraceAbandonRun("Metadata for $key is null, but is not permitted to be.")

      m_Metadata[key] = ParameterSetting(null, m_Force = true, m_Resolved = true)
      return null
    }



    /**************************************************************************/
    /* If we already have a value, we can return that. */

    if (existingValue.m_Resolved)
    {
//      Dbg.dCont(existingValue.m_Value ?: "XXX", "Biblica® মুক্তভাবে বাংলা সমকালীন সংস্করণের™")
      return existingValue.m_Value
    }



    /**************************************************************************/
    val res = expandConfigData(existingValue.m_Value!!)

    if (null == res && !nullsOk)
        throw StepExceptionWithoutStackTraceAbandonRun("Metadata for $key is null, but is not permitted to be.")

    if (null != res && res.startsWith("%%%"))
      throw StepExceptionWithStackTraceAbandonRun("ConfigData.get for $key: value was recorded as $res and no value has been supplied.")

    m_Metadata[key] = ParameterSetting(res, m_Force = true, m_Resolved = true)
    //Dbg.dCont(res ?: "XXX", "Biblica® মুক্তভাবে বাংলা সমকালীন সংস্করণের™")
    return res
  }


  /****************************************************************************/
  class VerboseErrorListener (private val inputString: String) : BaseErrorListener()
  {
    /**************************************************************************/
    private fun underlineError (recognizer: Parser, offendingToken: Token, line: Int, charPositionInLine: Int)
    {
      //val input = recognizer.inputStream.toString()
      //val lines = input.lines()
      //val errorLine = lines[line - 1]
      Dbg.d(inputString)
      Dbg.d(" ".repeat(charPositionInLine) + "^")
    }


    /**************************************************************************/
    override fun syntaxError (
        recognizer: Recognizer<*, *>,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    )
    {
      underlineError(recognizer as Parser, offendingSymbol as Token, line, charPositionInLine)
      val offendingToken = offendingSymbol.toString()
      val errorMessage = "Syntax error at line $line:$charPositionInLine. Offending symbol: $offendingToken. $msg"
      throw IllegalArgumentException(errorMessage)
    }
  }


  /****************************************************************************/
  /* Used to crawl over the parse tree and generate actual config values.
     Rather than having String? as the return value, I have () -> String?
     This makes it possible to do lazy evaluation, so that, for instance,
     $choose only evaluates as many arguments as are needed to determine its
     value. */

  private class ParseTreeVisitor: configDataParserBaseVisitor<() -> String?>()
  {
    /**************************************************************************/
    private val C_Dbg = false


    /**************************************************************************/
    private fun applyFn (fn: String, argFuncs: List<() -> String?>): String?
    {
      when (fn.lowercase())
      {
        "choose" -> // $choose($a, $b, ... ["Hello"]) Returns the first non-null argument
        {
          for (f in argFuncs)
          {
            val x = f()
            if (null != x)
              return x
          }

          return null
        }


        "delimited" -> // $delimited(bra, content, ket) Returns null if content is null, otherwise bra + content + ket.
        {
          val bra     = argFuncs[0]()
          val content = argFuncs[1]()
          val ket     = argFuncs[2]()
          return if (content.isNullOrBlank()) "" else (bra ?: "") + content + (ket ?: "")
        }


        "eq" -> // $eq($a, $b, $c, $d) When $c and $d are absent, Yes or No, according as the two arguments are or are not equal.  Otherwise $c or $d (null if no $d).
        {
          val arg1 = argFuncs[0]()
          val arg2 = argFuncs[1]()
          val arg3 = if (argFuncs.size > 2) argFuncs[2]() else null
          val arg4 = if (argFuncs.size > 3) argFuncs[3]() else null
          val match = if (arg1.equals(arg2, ignoreCase = true)) "Yes" else "No"

          return if (null == arg3)
              match
            else when(match)
            {
              "Yes" -> arg3
              else  -> arg4
            }
        }


        "getexternalxml" -> // $getExternalXml(fileSelector, args ...)  Returns a value from an external metadata file.
        {
           return if (null == m_ExternalDataHandler)
             null
           else
           {
             val expandedArgs = argFuncs.map { it()!! }
             val otherArgs = expandedArgs.subList(1, expandedArgs.size)
             m_ExternalDataHandler!!.getValue(expandedArgs[0], *otherArgs.toTypedArray())
           }
        }


        "indirect" ->
        {
          var ref = ""
          for (arg in argFuncs)
            ref += arg() ?: return null
          return getInternal(ref, true)
        }


        "join" -> // $join(sep, $a, $b, ...)  Returns a string in which the non-null arguments are separated by sep.
        {
          var res = ""
          val sep = argFuncs[0]()
          for (arg in argFuncs.subList(1, argFuncs.size))
          {
            val x = arg() ?: return null
            if (res.isNotEmpty() && x.isNotEmpty())
              res += sep
            res += x
          }

          return res
        }


        "todate" -> // $toDate(outputFormat, inputFormat, text) Converts text (which is assumed to represent a date in format inputFormat to a date in format outputFormat.
        {
          val outputFormat = argFuncs[0]()!!
          val inputFormat  = argFuncs[1]()!!
          val text         = argFuncs[2]()!!
          val dt = LocalDate.parse(text.split("T")[0], DateTimeFormatter.ofPattern(inputFormat))
          return dt.format(DateTimeFormatter.ofPattern(outputFormat))
        }

        else ->
          throw StepExceptionWithoutStackTraceAbandonRun("Unknown metadata function: $fn.")

    } // when
  }


    /**************************************************************************/
    /* Called with any expression, and routes control to the appropriate
       handler. */

    override fun visitTopLevelExpression (ctx: configDataParserParser.TopLevelExpressionContext): () -> String?
    {
      //println("Visiting expression: ${ctx.text}")
      return when
      {
        ctx.functionCall() != null -> visit(ctx.functionCall())
        ctx.variableRef()  != null -> visit(ctx.variableRef())
        ctx.miscellaneousText() != null -> visit(ctx.miscellaneousText())
        ctx.quotedString() != null -> visit(ctx.quotedString())
        else -> throw StepExceptionWithStackTraceAbandonRun("Bad case in antlr")
          //-> { -> null }
      }
    }

    /**************************************************************************/
    /* Amalgamates a number of separate elements.  If there is only a single
       element, I assume it was a reference lookup, and assume also that it's
       ok for it to be null.  Otherwise, if the entries contain any nulls, it's
       an error, because I can't combine them all. */

    override fun visitExpressionSequence (ctx: configDataParserParser.ExpressionSequenceContext): () -> String?
    {
      val exprFuncs = ctx.topLevelExpression().map { exprCtx -> visit(exprCtx) } // List<() -> String?>
      return {
        val res = exprFuncs.map { it.invoke() }
        if (exprFuncs.size <= 1)
          res[0]
        else
        {
          val nonNull = res.filterNotNull()
          if (nonNull.size == res.size)
            res.joinToString(separator = "").replace("\\\"", "\"")
          else
            throw StepExceptionWithStackTraceAbandonRun("Null entries found in ${res.joinToString(separator = "") { it ?: "NULL" }}")
        }
      }
    }


    /**************************************************************************/
    override fun visitFile (ctx: configDataParserParser.FileContext): () -> String?
    {
      return visit(ctx.expressionSequence())
    }


    /**************************************************************************/
    /* Called with a quoted string (ie a string in double quotes) and returns
       the string itself. */

    override fun visitQuotedString (ctx: configDataParserParser.QuotedStringContext): () -> String?
    {
      return { ctx.QUOTED_STRING().text.trim('"') }
    }


    /**************************************************************************/
    /* This one is a law unto itself because I need to be able to retain
       whitespace exactly as-is in non-quoted text strings, and it seems to be
       difficult if not impossible to do that relying upon the lexer and parser
       alone. */
       
    override fun visitMiscellaneousText (ctx: configDataParserParser.MiscellaneousTextContext): () -> String?
    {
      val startIndex = ctx.start.startIndex
      val stopIndex = ctx.stop.stopIndex
      val inputStream = ctx.start.inputStream
      return { inputStream.getText(Interval.of(startIndex, stopIndex)) }
    }


    /**************************************************************************/
    /* Receives something like $myVar, and needs to return any associated value.
       I have to permit nulls as an option here, because this may be being
       called, say, from $choose, and there a null argument is perfectly
       permissible. */

    override fun visitVariableRef (ctx: configDataParserParser.VariableRefContext): () -> String?
    {
        return {
          val res = getInternal(ctx.DOLLAR_IDENT().text.substring(1), true)
          if (C_Dbg) Dbg.d(ctx.DOLLAR_IDENT().text + " -> " + res)
          res
        }
    }


    /**************************************************************************/
    override fun visitFunctionCall (ctx: configDataParserParser.FunctionCallContext): () -> String?
    {
      val fn = ctx.DOLLAR_IDENT().text
      val argFuncs: List<() -> String?> =
        ctx.arguments()
            ?.argument()
            ?.map {
              exprCtx -> { visit(exprCtx).invoke() } // wrap the visit in a lambda
            } ?: emptyList()

        return { applyFn(fn.substring(1), argFuncs) }
    }
  }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                           Book descriptors                             **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    @Synchronized fun clearVernacularBibleDetails () = m_BookDescriptors.clear()


    /****************************************************************************/
    /**
    * Returns a list of vernacular book descriptors, if these are known (an
    * empty list if not).  The list follows the ordering specified in the
    * metadata, so if that has indicated that books are required to be out of
    * order, that's what you'll get.
    *
    * If the config data contains the necessary details, then the data structure
    * which this relies upon should have been populated as a result of
    * processing that.  If we are reliant upon taking the data from external
    * metadata files, we rely upon that having run already and used
    * processVernacularBibleDetails below to populate things.
    *
    * @return Book descriptors.
    */

    @Synchronized fun getBookDescriptors (): List<VernacularBookDescriptor>
    {
      if (m_BookDescriptors.isNotEmpty()) return m_BookDescriptors

      m_BookDescriptors = BibleBookNamesUsx.getBookDescriptors().toMutableList()
      return m_BookDescriptors
    }


    /****************************************************************************/
    /* Processes a single line of book details, which needs to be of the form:

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
    * Does what it says on the tin.  Intended presently only for use by the
    * various reference readers and writers.
    *
    * @param prefix The prefix for configuration parameters.
    */

    @Synchronized fun getValuesHavingPrefix (prefix: String): Map<String, String?>
    {
        val res: MutableMap<String, String?> = mutableMapOf()
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
//        Dbg.d(">>>" + theLine)
//        Dbg.dCont(theLine, "weird")



        /**************************************************************************/
        val forced = "#=" in theLine
        var line = theLine.split(Regex("="), 2)[1]
        line = line.substring(line.indexOf("=") + 1).trim() // Get the RHS.
        line = line.substring(0, line.length - 1) // Remove the closing paren.
        line = expandConfigData(line)!!.trim()
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
  /****************************************************************************/
  /**                                                                        **/
  /**       Calculated fields and functions used in calculated fields        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
     Most configuration parameters are overtly supplied on the command line,
     in configuration files, or via an environment variable.

     But there are also some which are generated internally -- you still refer
     to the parameter name in the normal way in order to access it, but instead
     of its value being stored within the data from the outset, it is calculated
     the first time it is used.  Many of these are used simply as intermediates
     which are needed in order to create compound fields.  All of these are
     handled via the data structure m_DataCalculators: some of the necessary
     code is embedded within that, and in more complex cases, it calls upon
     methods in this present section with names starting calculatedParameter_.

     There are also a few functions which can be included in parameter
     definitions.  These are marked out from the calculated parameters in
     that they all take arguments.  They are handled by applyMetadataFunction
     in this section.
   */

  /****************************************************************************/
  /* Bible name used in Bible chooser.

     General format was:

       canonicalEnglishName (canonicalEnglishAbbrev) / canonicalVernacularName (canonicalVernacularAbbrev)

     where the canonical name is the name as supplied, but with mentions of
     'New Testament' etc reduced to standard format.

     The English portion is always present.  The vernacular portion is omitted
     on English Bibles, and in cases where the English and vernacular forms are
     very similar.


     The general format now is:

       canonicalEnglishName / canonicalVernacularName

     I have now removed the abbreviations which appeared previously because it
     appears that something else -- something outside of my control -- is adding
     the abbreviation itself.

     I have retained the bulk of the code (along with a few bits which I have
     commented out) just in case we need to revert to the old format.
     */

  private fun calculatedParameter_BibleNameForBibleChooser (): String
  {
    /**************************************************************************/
    val englishTitle = getInternal("calcSelectedBibleNameEnglishForBibleChooser", nullsOk = false)!!
    val abbrevEnglish = getInternal("calcSelectedAbbreviationEnglishForBibleChooser", nullsOk = false)!!
    var vernacularTitle = getInternal("calcSelectedBibleNameVernacularForBibleChooser", nullsOk = true) ?: ""
    var vernacularAbbreviation = getInternal("calcSelectedAbbreviationVernacularForBibleChooser", nullsOk = true) ?: ""



    /**************************************************************************/
    /* Reduce the English title to canonical form. */

    val englishTitleCanonicalised = englishTitle.replace("\\s+".toRegex(), " ").trim()
      .replace("(?i)New Testament".toRegex(), "NT")
      .replace("(?i)Old Testament".toRegex(), "OT")
      .replace("(?i)N\\.\\s?T\\.".toRegex(), "NT")
      .replace("(?i)O\\.\\s?T\\.".toRegex(), "OT")



    /**************************************************************************/
    /* Null out the vernacular name if we're dealing with an English text
       or if the vernacular and English names are very similar.  This
       isn't _guaranteed_ to avoid having very similar names, but it's a
       reasonable stab at it. */

    if (vernacularTitle.isNotEmpty())
    {
      val englishModified = StepStringUtils.removePunctuationAndSpaces(englishTitle)
      val vernacularModified = StepStringUtils.removePunctuationAndSpaces(vernacularTitle)

      if ("eng".equals(get("calcLanguageCode3Char"), ignoreCase = true)   ||
        englishModified.contains(vernacularModified, ignoreCase = true)   ||
        vernacularModified.contains(englishModified, ignoreCase = true))
      vernacularTitle = ""
    }



    /**************************************************************************/
    /* The English abbreviation is what it is.  The vernacular abbreviation is
       set to an empty string if it is the same as the English. */

    if (abbrevEnglish.equals(vernacularAbbreviation, ignoreCase = true))
      vernacularAbbreviation = ""



    /**************************************************************************/
    var res = englishTitleCanonicalised
    if (vernacularTitle.isNotEmpty() && vernacularTitle.lowercase() != englishTitleCanonicalised.lowercase())
      res = "$res / $vernacularTitle"

//    var res = if (abbrevEnglish.lowercase() in englishTitleCanonicalised.lowercase()) englishTitleCanonicalised else "$englishTitleCanonicalised ($abbrevEnglish)"
//    var vernacularBit = (if (vernacularAbbreviation.lowercase() in vernacularTitle.lowercase()) vernacularTitle else "$vernacularTitle ($vernacularAbbreviation)").trim()
//    if (vernacularBit.contains("()")) vernacularBit = vernacularBit.replace(" ()", "")
//    if (vernacularBit.isNotEmpty()) res += " / $vernacularBit"

    return res
  }


  /****************************************************************************/
  /* Simplest if I just copy the relevant portion of the spec :-

     - The part of the Bible (OT+NT+Apoc) is only stated if it doesn’t contain
       OT+NT.  This seems to suggest that you don't mention the Apocrypha on
       a text which contains OT+NT even if it's present, and I suspect that's
       not what's meant.

     - If there are five books or fewer, they are listed (eg OT:Ps; Lam +NT).

     - If there are more than five books, but not the complete set, mark the
       text as eg 'OT incomplete +NT'.

     Note that I make the assumption here that this is being called late in
     the day, and that we therefore want to work with the _OSIS_ internal
     data collection. */

  private fun calculatedParameter_ScriptureCoverage (): String?
  {
    /**************************************************************************/
    val bookNumbers = InternalOsisDataCollection.getBookNumbers()



    /**************************************************************************/
    val C_MaxIndividualBooksToReport = 5
    val otBooks = bookNumbers.filter{ BibleAnatomy.isOt(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
    val ntBooks = bookNumbers.filter{ BibleAnatomy.isNt(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
    val dcBooks = bookNumbers.filter{ BibleAnatomy.isDc(it) }.map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }



    /**************************************************************************/
    var fullOt = otBooks.size == BibleAnatomy.getNumberOfBooksInOt()
    val fullNt = ntBooks.size == BibleAnatomy.getNumberOfBooksInNt()



    /**************************************************************************/
    if (!fullOt && otBooks.isNotEmpty())
    {
      var nOtBooks = otBooks.size
      if (dcBooks.contains("Dag") && !otBooks.contains("Dan")) ++nOtBooks
      if (dcBooks.contains("Esg") && !otBooks.contains("Est")) ++nOtBooks
      fullOt = nOtBooks == BibleAnatomy.getNumberOfBooksInOt()
    }



    /**************************************************************************/
    if (fullOt && fullNt) return if (dcBooks.isEmpty()) null else "+DC"
    if (fullOt && ntBooks.isEmpty()) return if (dcBooks.isEmpty()) "OT only" else "OT+DC only"
    if (fullNt && otBooks.isEmpty()) return if (dcBooks.isEmpty()) "NT only" else "DC+NT only"



    /**************************************************************************/
    if (ntBooks.isEmpty() && otBooks.isEmpty()) return "DC only"



    /**************************************************************************/
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



    /**************************************************************************/
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



    /**************************************************************************/
    val apocPortion = if (dcBooks.isEmpty()) "" else " DC "



    /**************************************************************************/
    val haveNtPortion = ntPortion.isNotEmpty()
    val haveApocPortion = apocPortion.isNotEmpty()
    var res = otPortion + if (otPortion.isNotEmpty() && (haveNtPortion || haveApocPortion)) " + " else ""
    res += ntPortion + if (haveApocPortion) " + " else ""
    res += apocPortion

    if (res.startsWith("+ ")) res = res.substring(2)

    return res.ifEmpty { null }
  }


  /****************************************************************************/
  private fun calculatedParameter_calcTextSource (): String
  {
    var textSource = ConfigData["calcSelectedTextRepositoryOrganisationAbbreviatedName"] ?: "Unknown"

    var ownerOrganisation = get("swordTextOwnerOrganisationFullName")!!
    if (ownerOrganisation.isNotEmpty()) ownerOrganisation = "&nbsp;&nbsp;Owning organisation: $ownerOrganisation"

    var textId: String = ConfigData["swordTextIdSuppliedBySourceRepositoryOrOwnerOrganisation"] ?: ""
    if (textId.isBlank() || "unknown".equals(textId, ignoreCase = true)) textId = ""

    val textCombinedId =
      if (textId.isNotEmpty())
        "Version $textId"
      else
        ""

    return "$textSource $ownerOrganisation $textCombinedId"
  }

  /****************************************************************************/
  /* This is a year used to identify the text.  It's a little difficult to work
     out what it should be, in part because every text appears to be a law
     unto itself, and in part because I'm really not at all sure what this is
     supposed to achieve.  I _think_ it works as follows:

     If the English or vernacular text contains something which matches yyyy,
     we assume that's the year we want.  Except that that means it's already
     visible, so in this case we return null.

     Otherwise, we work through the following:

     - Any overtly specified year.
     - Any yyyy figure available from fields likely to contain copyright
       details.
  */

  private fun calculatedParameter_DateForBibleChooser (): String?
  {
    /**************************************************************************/
    val yyyyRegex = Regex("(?<!\\d)\\d{4}(?!\\d)")



    /**************************************************************************/
    /* If there is something in the Bible name which looks like a date, assume
       that is, indeed, already what we need, and so there is no need to return
       a value. */

    val bibleName = get("calcBibleNameForBibleChooser")!!
    val match = yyyyRegex.find(bibleName)
    if (null != match)
      return null // match.value



    /**************************************************************************/
    val mainPat = Regex("(?i)(&copy;|©|(copyright))\\W*(?<years>\\d{4}(\\W+\\d{4})*)") // &copy; or copyright symbol or the word 'copyright' followed by any number of blanks and four digits.
    val subPat = Regex("(?<year>\\d{4})$") // eg ', 2012' -- ie permits the copyright details to have more than one date, in which case we take the last.
    var res: String? = null



    /**************************************************************************/
    for (key in listOf("swordCopyrightStatement", "swordShortCopyright"))
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



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  /* List of options taken from the documentation mentioned above.
     don't change the ordering here -- it's not entirely clear whether
     order matters, but it may do.

     Note, incidentally, that sometimes STEP displays an information button at
     the top of the screen indicating that the 'vocabulary feature' is not
     available.  This actually reflects the fact that the Strong's feature is
     not available in that Bible.

     I am not sure about the inclusion of OSISLemma below.  OSIS actually
     uses the lemma attribute of the w tag to record Strong's information,
     so I'm not clear whether we should have OSISLemma if lemma appears at
     all, even if only being used for Strong's; if it should be used if there
     are occurrences of lemma _not_ being used for Strong's; or if, in fact,
     it should be suppressed altogether.
  */

  private fun calculatedParameter_calcSwordOptions (): String
  {
    var res = ""
    FeatureIdentifier.process(FileLocations.getInternalOsisFilePath())
    if ("ar" == ConfigData["calcLanguageCode2Char"]) res += "GlobalOptionFilter=UTF8ArabicPoints\n"
    if (FeatureIdentifier.hasLemma()) res += "GlobalOptionFilter=OSISLemma\n"
    if (FeatureIdentifier.hasMorphologicalSegmentation()) res += "GlobalOptionFilter=OSISMorphSegmentation\n"
    if (FeatureIdentifier.hasStrongs()) res += "GlobalOptionFilter=OSISStrongs\n"
    if (FeatureIdentifier.hasFootnotes()) res += "GlobalOptionFilter=OSISFootnotes\n"
    if (FeatureIdentifier.hasScriptureReferences()) res += "GlobalOptionFilter=OSISScriprefs\n" // Crosswire doc is ambiguous as to whether this should be plural or not.
    if (FeatureIdentifier.hasMorphology()) res += "GlobalOptionFilter=OSISMorph\n"
    if (FeatureIdentifier.hasNonCanonicalHeadings()) res += "GlobalOptionFilter=OSISHeadings\n"
    if (FeatureIdentifier.hasVariants()) res += "GlobalOptionFilter=OSISVariants\"\n"
    if (FeatureIdentifier.hasRedLetterWords()) res += "GlobalOptionFilter=OSISRedLetterWords\n"
    if (FeatureIdentifier.hasGlosses()) res += "GlobalOptionFilter=OSISGlosses\n"
    if (FeatureIdentifier.hasTransliteratedForms()) res += "GlobalOptionFilter=OSISXlit\n"
    if (FeatureIdentifier.hasEnumeratedWords()) res += "GlobalOptionFilter=OSISEnum\n"
    if (FeatureIdentifier.hasGlossaryLinks()) res += "GlobalOptionFilter=OSISReferenceLinks\n"
    if (FeatureIdentifier.hasStrongs()) res += "Feature=StrongsNumbers\n"
    //??? if (!FeatureIdentifier.hasMultiVerseParagraphs()) res += "Feature=NoParagraphs\n"
    return res
  }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    private fun tidyVal (vv: String): String
    {
        val v = vv.trim()
        if ("{empty}".equals(v, ignoreCase = true)) return ""
        return v.trim().replace(Regex("(?i)\\{space}"), " ")
    }


    /****************************************************************************/
    data class ParameterSetting (var m_Value: String?, var m_Force: Boolean, var m_Resolved: Boolean = false)


    /****************************************************************************/
    private var m_BookDescriptors: MutableList<VernacularBookDescriptor> = ArrayList()
    private val m_CopyAsIsLines: MutableList<String> = ArrayList()
    private val m_EnglishDefinitions: MutableSet<String> = mutableSetOf()
    private var m_ExternalDataHandler: ConfigDataExternalFileInterfaceBase? = null
    private var m_Initialised: Boolean = false
    private val m_Metadata: MutableMap<String, ParameterSetting?> = ConcurrentHashMap()
    private val m_RawUsxToOsisTagTranslationLines: MutableList<String> = ArrayList()
    private val m_RegexesForForcedOsisPreprocessing: MutableList<Pair<Regex, String>> = mutableListOf()
    private val m_RegexesForGeneralInputPreprocessing: MutableList<Pair<Regex, String>> = mutableListOf()
    private val m_SharedConfigFolderPathAccesses: MutableSet<String> = mutableSetOf()
    private val m_UsxToOsisTagTranslationDetails: MutableMap<String, Pair<String, TagAction>> = TreeMap(String.CASE_INSENSITIVE_ORDER)


   /****************************************************************************/
   /* Used for debug purposes. */

    private object ConfigFilesStack
    {
      fun getSummary () = m_Summary.ifEmpty { "Calculated" }

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
        File(filePath).forEachLine { if (processLine(it)) throw StepExceptionNotReallyAnException("")  }
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
  private fun getCombinedEnglishAndVernacular (key: String): String
  {
    val english    = TranslatableFixedText.stringFormatWithLookupEnglish(key)
    val vernacular = TranslatableFixedText.stringFormatWithLookup       (key)
    var res = english
    if (vernacular != english) res += " / $vernacular"
    return res
  }


  /****************************************************************************/
  /* Various methods which calculate config data on demand.  These are
     used where the value is associated with a parameter name (the name being
     the one which appears as the key in each entry below).  There is a separate
     block which handles situations where the processing requires a function to
     calculate it. */

  private val m_DataCalculators: Map<String, () -> String?> = mapOf(
    /**************************************************************************/
    /* Items which feed into the Bible chooser text.  (This is also duplicated
       in the copyright / description block.) */

    "calcBibleNameForBibleChooser" to { calculatedParameter_BibleNameForBibleChooser() },

    "calcCountriesWhereLanguageUsed" to
     {
        val languageCode = get("calcLanguageCode3Char")!!.lowercase()
        val C_CodesForVeryWidelyUsedLanguages = ".grc.hbo.ara.arb.chi.cmn.deu.eng.fra.fre.ger.nld.por.spa."
        val C_CodesForSingleUseLanguages = ".alb.sqi.arm.hye.aze.bul.cze.ces.est.fin.geo.kat.hun.ice.isl.jpn.kaz.kir.lav.lit.mon.pol.rum.ron.slo.slk.tgk.tha.tuk.uzb.vie."
        if (languageCode in C_CodesForVeryWidelyUsedLanguages || languageCode in C_CodesForSingleUseLanguages)
          null
        else
          "${IsoLanguageAndCountryCodes.getCountriesWhereUsed(languageCode)}."
     },

    "calcLanguageDetailsForBibleChooser" to
    {
        val languageName = get("calcLanguageNameInEnglish")!!
        if ("english".equals(languageName, ignoreCase = true))
          null
        else if (get("stepBibleNameEnglishAsSupplied")!!.contains(languageName, ignoreCase = true))
          null
        else
          "In $languageName"
    },

    "calcScriptureCoverageForBibleChooserForBibleChooser" to { calculatedParameter_ScriptureCoverage() },

    "calcDateForBibleChooser" to { calculatedParameter_DateForBibleChooser() },



    /**************************************************************************/
    /* Items which end up on the copyright / description block. */

    /*----------------------------------------------------------------------*/
    "calcAddedFeaturesForCopyrightPage" to
    {
      if (getAsBoolean("stepIsCopyrightText") && false) // $$$ 01-Aug-2020.
        ""
      else
      {
        val addedValue: MutableList<String> = mutableListOf()
        if (getAsBoolean("stepAddedValueMorphology", "No")) addedValue.add(getCombinedEnglishAndVernacular("V_addedValue_Morphology"))
        if (getAsBoolean("stepAddedValueStrongs", "No")) addedValue.add(getCombinedEnglishAndVernacular("V_addedValue_Strongs"))
        if (getAsBoolean("calcAddedFootnotes")) addedValue.add(getCombinedEnglishAndVernacular("V_modification_FootnotesMayHaveBeenAdded"))

        if (addedValue.isEmpty())
          ""
        else
          addedValue.joinToString("<br>- ", prefix = "- ", postfix = "<br>")
      }
    },



    /*----------------------------------------------------------------------*/
    "calcAmendmentsAppliedForCopyrightPage" to
    {
      if (getAsBoolean("stepIsCopyrightText") && false) // $$$ 02-Aug-2026.
        ""
      else
      {
        /**********************************************************************/
        val amendments: MutableList<String> = mutableListOf()



        /**********************************************************************/
        /* If we're using a Crosswire versification scheme, osis2mod may have
           restructured the text to force it into the right shape.  We don't
           have control over what, if anything, it does, so best to assume
           that it may indeed have changed.

           Note that if we are using a non-Crosswire scheme, it will be
           because we are putting it through our own version of osis2mod,
           which doesn't require us to change the structure, so for that,
           there's nothing to report. */

        val versificationScheme = get("stepVersificationScheme")!!
        if (versificationScheme.lowercase() in VersificationSchemesSupportedByOsis2mod.getSchemes().map { it.lowercase() })
        {
          val english    = TranslatableFixedText.stringFormatWithLookupEnglish("V_modification_VerseStructureMayHaveBeenModified", ConfigData["stepVersificationScheme"]!!)
          val vernacular = TranslatableFixedText.stringFormatWithLookup       ("V_modification_VerseStructureMayHaveBeenModified", ConfigData["stepVersificationScheme"]!!)
          var s = english
          if (vernacular != english) s += " / $vernacular"
          amendments.add(s)
        }



        /**********************************************************************/
        val deletedBooks = ConfigData["calcDeletedBooks"]
        if (null != deletedBooks)
          amendments.add("Software limitations mean we have had to remove the following books: ${deletedBooks}.")



        /************************************************************************/
        amendments += Issues.getCopyrightPageStatementsFromIssuesList()
        if (amendments.isEmpty())
          null
        else
          amendments.joinToString("<br>- ", prefix = "- ", postfix = "<br>")
      }
    },



    /**************************************************************************/
    /* Items which end up in the Sword configuration file.  Unless indicated to
       the contrary, items with names starting 'sword' supply values for
       Sword config parameters of the same name (but devoid of the word Sword').
       Items starting 'step' represent information generated by the processing
       here and not actually required by Sword, but added as stylised comments
       in the Sword config file for admin purposes. */

    "calcExtendedLanguageCode" to // Sword config file.
    {
      val languageCode = get("calcLanguageCode2Char")!!
      val script = get("calcScriptCodeAsSupplied")!!
      val country = get("calcCountryCodeAsSupplied", "")
      var res = listOf(languageCode, script, country).joinToString("-")
      while (res.endsWith("-")) res = res.substring(0, res.length - 1)
      res
    },

    "calcModuleCreationDate" to { SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(Date()) },

    "calcJarVersion" to { MiscellaneousUtils.getJarVersion() },

    "stepTextModifiedDate" to { SimpleDateFormat("dd-MMM-yyyy").format(Date()) },

    "calcSwordTextCategory" to { if ("zText" == get("swordModDrv", "zText")) "Biblical Texts" else "Commentaries" },

    "calcSwordDataPath" to {
      val firstPart = if ("zText" == get("swordModDrv", "zText")) "./modules/texts/ztext/" else "./modules/comments/zcom/"
      Paths.get(firstPart, get("calcModuleName")).toString().replace("\\", "/") + "/"
    }, // Sword config file.

    "calcInputFileDigests" to { Digest.makeFileDigests() },

    "calcLanguageNameInEnglish" to { IsoLanguageAndCountryCodes.getLanguageName(getInternal("calcLanguageCode3Char", false)!!) },

    "calcSwordOptions" to { calculatedParameter_calcSwordOptions() },

    "swordTextDirection" to // See notes for calcTextDirection.
      {
        val textDirectionAsSupplied = ConfigData["textDirectionAsSupplied"] ?: Unicode.getTextDirection(m_SampleText)
        val x = textDirectionAsSupplied.uppercase()
        if ("LTR" == x || "LTOR" == x) "LtoR" else "RtoL"
      },

    "calcTextSource" to { calculatedParameter_calcTextSource() },
  )
}





/******************************************************************************/
/**
 * Creates a zip file to hold all of the configuration files used on the
 * present run (excluding material in the Resources area of the JAR file),
 * and, where being used on a subsequent run, provides access to it.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigArchiver
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns a list of all of those config files (other than the root files and
   * built-in ones) used by this run.  I used to save these as part of the
   * repository package, so they were available should we need to rebuild
   * things.  I have since concluded that this is not particularly useful,
   * but have retained the method just in case.
   *
   * @return List of file paths.
   */

  fun getConfigFilesUsedByThisRun (): List<String>
  {
    return m_FileNames.values.mapNotNull { fileName ->
      if (null == fileName)
        null
      else if ("step.conf" == fileName || "step.xlsx" == fileName || fileName.contains("jarResources", ignoreCase = true))
        null
      else
        FileLocations.getConfigFileInputPath(fileName)
      }
  }


  /****************************************************************************/
  /**
  * Returns a list of translatable text entries used (or potentially used) in
  * this run.
  *
  * This includes all of the special entries, all of the English entries, and
  * any entries for this text's language which contain actual values.
  *
  * @return List of strings.
  */

  fun getTranslationTextUsedByThisRun (): List<String>
  {
    val translationsDbPath = FileLocations.getVernacularTextDatabaseFilePath()
    val languageCode = ConfigData["calcLanguageCode3Char"]!!
    return File(translationsDbPath).readLines().filter {
      it.startsWith("Special:") ||
      it.startsWith("eng|")     ||
      (it.startsWith("$languageCode|") && "#Untranslatable#" !in it )
    }
  }


  /****************************************************************************/
  /**
   * Obtains data either from the previous-config-zip-file (if taking config
   * from there), or from an appropriate file location.  Throws an error if no
   * file of the given *name* can be found.  Note that we are indeed concerned
   * only with names here, so you need to avoid setting things up with more
   * than one file with a given name.  Throws an exception if the file cannot
   * be found
   *
   * @param pseudoFilePath Input file path.
   *
   * @return Lines from file, or null if the file has already been loaded.
   */

  fun getDataFrom (pseudoFilePath: String, okIfNotExists: Boolean): List<String>?
  {
    /**************************************************************************/
    /* Pseudo file path may start @jarResources or it may be a full path. */

    val fileName = File(pseudoFilePath).name
    val filePath = FileLocations.getConfigFileInputPath(pseudoFilePath)



    /**************************************************************************/
    /* Check if we've already loaded the file -- and give up if we have.
       Otherwise record the file name and the path for use when creating the
       archival zip later -- except that we don't save stuff which is in the
       resources section of the JAR, because that doesn't get archived. */

    if (null != m_FileNames[fileName])
      return null

    if ("\$jar" !in pseudoFilePath)
      m_FileNames[fileName] = filePath



    /**************************************************************************/
    return getLinesFromRealFile(filePath!!, okIfNotExists)
  }


  /****************************************************************************/
  private fun addTextDataToZip (zipOut: ZipOutputStream, entryName: String, lines: List<String>)
  {
    zipOut.putNextEntry(ZipEntry(entryName))

    BufferedWriter(OutputStreamWriter(zipOut, Charsets.UTF_8)).use { writer ->
        lines.forEach { writer.write(it + "\n") }
    }
  }


  /****************************************************************************/
  /* Obtains the configuration lines from a given file, which may be specified
     as being relative to the root folder, relative to the calling file (if
     supplied) or within the JAR.

     The returned list will have had comments and blank lines removed and
     continuation lines combined into a single line. */

  private fun getLinesFromRealFile (filePath: String, okIfNotExists: Boolean): List<String>
  {
    try
    {
      val fileDetails = FileLocations.getInputStream(filePath)
      val rawLines = fileDetails.first!!.bufferedReader().use { it.readText() } .lines()
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
        if (!okIfNotExists) throw StepExceptionWithoutStackTraceAbandonRun("Could not find config file $filePath")
        return listOf()
    }
  }


  /****************************************************************************/
  private val m_FileNames: MutableMap<String, String?> = mutableMapOf()
}

