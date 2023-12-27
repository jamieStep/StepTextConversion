package org.stepbible.textconverter

import org.stepbible.textconverter.TextConverterProcessorEvaluateVersificationSchemes.evaluateSingleScheme
import org.stepbible.textconverter.support.bibledetails.BibleStructure
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/******************************************************************************/
/**
 * Sadly, this is a bit of a mish-mash.  It contains various things to do with
 * establishing the overall processing environment which have no natural home.
 *
 * @author ARA "Jamie" Jamieson
 */

object GeneralEnvironmentHandler
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * For use at the end of a run.  Returns major warnings as simulated large
  * characters to output to stderr.
  */

  fun getMajorWarningsAsBigCharacters (): String
  {
    // The try below is required because if we start from OSIS, we won't have any reversification details.
    var res = ""
    try { if (!ConfigData["stepReversificationDataLocation"]!!.startsWith("http")) res += C_Local_ReversificationData } catch (_: Exception) {}
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) res += C_NotEncrypted
    if ("evaluationonly" == ConfigData["stepRunType"]!!.lowercase()) res += C_NonRelease
    return res
  }


  /****************************************************************************/
  /**
   * Sets up details of any general command-line parameters which nothing else
   * is likely to deal with.
   *
   * @param commandLineProcessor Command line processor.
   */

  fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("reversificationType", 1, "When reversification is to be applied (if at all)", listOf("None", "RunTime", "ConversionTime"), "None", false)
    commandLineProcessor.addCommandLineOption("reversificationFootnoteLevel", 1, "Type of reversification footnotes", listOf("Basic", "Academic"), "Basic", false)
    commandLineProcessor.addCommandLineOption("updateReason", 1, "A reason for creating this version of the module (required only if runType is Release and the release arises because of changes to the converter as opposed to a new release from he text suppliers).", null, "Unknown", false)
  }


  /****************************************************************************/
  /**
  * Sets up portions of the environment which need to be sorted early on.
  */

  fun onStartup ()
  {
    determineOsis2modAndReversificationType()   // Are we going to reversify, and what flavour of osis2mod do we need?
    Osis2ModInterface.instance().initialise()   // Now we know which osis2mod flavour we're dealing with, do anything needed to set it up.
    determineModuleNameAudienceRelatedSuffix()  // Adds to the module name something which says whether this can be used only within STEP, or more widely.
    determineModuleNameTestRelatedSuffix()      // On test runs, adds to the module name something saying this version of the module is for evaluation only.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This goes on the end of every module we generate.  Some modules can be
     used only within STEP, and these get the suffix _sbOnly (ie for use only
     within STEPBible).  Some are intended for possible public consumption, and
     these get the suffix _sb (ie produced by STEPBible, and intended to make
     sure the names we give to them do not clash with the name of any existing
     module upon which they may have been based).

     At present 'for use only within STEPBible' and 'won't work outside of
     STEPBible' come down to the same thing.  A Bible is _sbOnly if:

     - It has been encrypted OR ...

     - We have run it through our own version of osis2mod.

     Stop press: We have just discovered that it's too painful to rename ESV
     to follow this new standard.  So I've added another option.  We are
     already using the root folder name to give us the language code and
     vernacular abbreviation (eg eng_ESV).  If the root folder name has a
     third element (eng_ESV_th), I take that as being the suffix.
  */

  private fun determineModuleNameAudienceRelatedSuffix ()
  {
    val forcedSuffix = ConfigData.parseRootFolderName("stepModuleSuffixOverride")
    if (forcedSuffix.isNotEmpty())
      ConfigData["stepModuleNameAudienceRelatedSuffix"] = "_$forcedSuffix"
    else
    {
      val isSbOnly = "step" == ConfigData["stepOsis2modType"] ||
                     ConfigData.getAsBoolean("stepEncryptionRequired")

      ConfigData["stepModuleNameAudienceRelatedSuffix"] = if (isSbOnly) "_sbOnly" else "_sb"
    }
  }


  /****************************************************************************/
  /* A potential further addition to module names.  On release runs, it adds
     nothing.  On non-release runs it adds a timestamp etc to the name, so that
     we can have multiple copies of a module lying around. */

  private fun determineModuleNameTestRelatedSuffix ()
  {
    ConfigData["stepModuleNameTestRelatedSuffix"] =
      if ("release" in ConfigData["stepRunType"]!!.lowercase())
        ""
      else
      {
        var x = ConfigData["stepRunType"]!!
        if ("evaluation" in x.lowercase()) x = "evalOnly"
        "_" + x + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
      }
  }


  /****************************************************************************/
  /* Hmm ... this gets rather complicated.

     You can, if you wish, force which version of osis2mod is used -- set
     stepForceOsis2modType to either 'crosswire' or 'step'.   This parameter
     feeds into stepOsis2modType.

     You can, if you wish, force the point at which reversification is applied.
     set stepForceReversificationType to 'none' to prevent reversification,
     to 'runTime' to have reversification applied on the fly when someone using
     STEP uses added value functionality which relies upon the text conforming
     to NRSVA.  Or use 'conversionTime' to have the text restructured during
     the conversion process.  The default is none.  'conversionTime' is
     probably applicable only in very limited circumstances, because it may
     produce a module which diverges significantly from the raw text in some
     places -- it is probably of use mainly with Public Domain Bibles, and even
     then only ones where it there is some good reason for accepting a
     restructured Bible.

     This parameter feeds into stepReversificationType.

     And finally you can also set the level of footnotes to be output where
     reversification applies, the options being Basic or Academic (default
     Basic).  The relevant parameter here is stepReversificationFootnoteLevel.

     Having said all of this, the processing is capable of making its own mind up
     as to what settings are appropriate.  Reversification is required, for
     instance, if the raw text diverges the 'wrong' way from any specified
     versification scheme (see below for explanation).

     If any stipulated values are at odds with the processing's own
     determination, it may honour them (but warn) or may raise an error.

     A deviation is in the right direction if the text lacks verses which
     osis2mod expects, given the specified versification scheme.  We regard this
     as ok, because everything seems to cope.  It is in the wrong direction if
     the text contains verses which osis2mod does _not_ expect because here
     osis2mod intervenes and starts moving text around.  If a text exhibits a
     combination of right and wrong deviations, it is regarded as deviating in
     the wrong direction.
  */

  private fun determineOsis2modAndReversificationType ()
  {
    /**************************************************************************/
    fun reversificationNeeded (): Boolean
    {
      val versificationScheme = ConfigData["stepVersificationSchemeCanonical"]!!
      val schemeEvaluation = evaluateSingleScheme(versificationScheme)!!
      return schemeEvaluation.booksMissingInOsis2modScheme > 0 || schemeEvaluation.versesMissingInOsis2modScheme > 0 || BibleStructure.UsxUnderConstructionInstance().hasSubverses()
    }



    /**************************************************************************/
    var reversificationType = (ConfigData["stepForceReversificationType"] ?: "tbd").lowercase()
    if (reversificationType.isEmpty()) reversificationType = "tbd"
    when (reversificationType)
    {
      "conversiontime" ->
      {
        if (!reversificationNeeded())
          Logger.warning("Conversion-time reversification specified (and honoured), but reversification not needed.")
      }

      "runtime" ->
      {
        if (!reversificationNeeded())
          Logger.warning("Run-time reversification specified (and honoured), but reversification not needed.")
      }

      "none" ->
      {
        if (reversificationNeeded())
          Logger.error("Reversification prohibited but text requires it.")
      }

      "tbd" ->
      {
        if (reversificationNeeded())
        {
          ConfigData["stepForceReversificationType"] = "runtime"
          Logger.info("Processing has decided to apply runtime reversification.")
        }
        else
        {
          ConfigData["stepForceReversificationType"] = "none"
          Logger.info("Processing has decided no reversification is needed.")
        }
      }
    }



    /**************************************************************************/
    ConfigData.delete("stepReversificationType")
    ConfigData["stepReversificationType"] = ConfigData["stepForceReversificationType"]!!
    val reversificationFootnoteLevel = ConfigData["stepReversificationFootnoteLevel"] ?: "basic"
    ConfigData.delete("stepReversificationFootnotesLevel")
    ConfigData["stepReversificationFootnotesLevel"] = reversificationFootnoteLevel



    /**************************************************************************/
    var osis2modType = (ConfigData["stepForceOsis2modType"] ?: "tbd").lowercase()
    if (osis2modType.isEmpty()) osis2modType = "tbd"
    when (osis2modType)
    {
      "step" ->
      {
        if (ConfigData["stepForceReversificationType"]!! in "none.conversiontime" && BibleStructure.UsxUnderConstructionInstance().versesAreInOrder())
          Logger.warning("Use of STEP osis2mod stipulated (and honoured), but the usual grounds for requiring (ie reversification or out of order verses) it do not apply.")
      }

      "crosswire" ->
      {
        if (ConfigData["stepForceReversificationType"]!! !in "none.conversiontime" || BibleStructure.UsxUnderConstructionInstance().versesAreInOrder())
          Logger.error("Use of Crosswire osis2mod stipulated, but text requires reversification, and Crosswire osis2mod won't work with that.")
      }

      "tbd" ->
      {
        if (ConfigData["stepForceReversificationType"]!! in "none.conversiontime" && BibleStructure.UsxUnderConstructionInstance().versesAreInOrder())
        {
          ConfigData["stepForceOsis2modType"] = "crosswire"
          Logger.info("Processing has decided to use Crosswire osis2mod.")
        }
        else
        {
          ConfigData["stepForceOsis2modType"] = "step"
          Logger.info("Processing has decided to use STEP osis2mod.")
        }
      }
    }



    /**************************************************************************/
    ConfigData.delete("stepOsis2modType")
    ConfigData["stepOsis2modType"] = ConfigData["stepForceOsis2modType"]!!
  }


/******************************************************************************/
// https://patorjk.com/software/taag/#p=display&f=Graffiti&t=Type%20Something%20  Font=Big.

private const val C_Local_ReversificationData ="""
 _      ____   _____          _        _____  ________      ________ _____   _____ _____ ______ _____ _____       _______ _____ ____  _   _ 
| |    / __ \ / ____|   /\   | |      |  __ \|  ____\ \    / /  ____|  __ \ / ____|_   _|  ____|_   _/ ____|   /\|__   __|_   _/ __ \| \ | |
| |   | |  | | |       /  \  | |      | |__) | |__   \ \  / /| |__  | |__) | (___   | | | |__    | || |       /  \  | |    | || |  | |  \| |
| |   | |  | | |      / /\ \ | |      |  _  /|  __|   \ \/ / |  __| |  _  / \___ \  | | |  __|   | || |      / /\ \ | |    | || |  | | . ` |
| |___| |__| | |____ / ____ \| |____  | | \ \| |____   \  /  | |____| | \ \ ____) |_| |_| |     _| || |____ / ____ \| |   _| || |__| | |\  |
|______\____/ \_____/_/    \_\______| |_|  \_\______|   \/   |______|_|  \_\_____/|_____|_|    |_____\_____/_/    \_\_|  |_____\____/|_| \_|                                                                                                                                                                                                                    
    
     """


/******************************************************************************/
private const val C_NonRelease = """
 _   _  ____  _   _     _____  ______ _      ______           _____ ______ 
| \ | |/ __ \| \ | |   |  __ \|  ____| |    |  ____|   /\    / ____|  ____|
|  \| | |  | |  \| |   | |__) | |__  | |    | |__     /  \  | (___ | |__   
| . ` | |  | | . ` |   |  _  /|  __| | |    |  __|   / /\ \  \___ \|  __|  
| |\  | |__| | |\  |   | | \ \| |____| |____| |____ / ____ \ ____) | |____ 
|_| \_|\____/|_| \_|   |_|  \_\______|______|______/_/    \_\_____/|______|
                                                                              
"""


/******************************************************************************/
private const val C_NotEncrypted = """
 _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
| \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
|  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
| |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
|_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                     
     """


}

// Use if we need to detect name-clashes here.
//
//   /**************************************************************************/
//    /* Check to see if there are any clashes against the Crosswire module
//       list. */
//
//    Dbg.reportProgress("Obtaining details of Crosswire modules so as to avoid name clashes")
//    val crosswireModules = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
//    val dataLocations = listOf(ConfigData["stepExternalDataPath_CrosswireModuleListBibles"]!!,
//                               ConfigData["stepExternalDataPath_CrosswireModuleListCommentaries"]!!,
//                               ConfigData["stepExternalDataPath_CrosswireModuleListDevotionals"]!!)
//    dataLocations.forEach { url ->
//      val lines = URL(url).readText().lines()
//      var i = -1
//      while (++i < lines.size)
//      {
//        var line = lines[i]
//        var ix = line.indexOf("jsp?modName")
//        if (ix < 0) continue
//
//        var name = line.substring(ix + "jsp?modName".length + 1)
//        ix = name.indexOf("\"")
//        name = name.substring(0, ix)
//
//        i += 2
//        val description = lines[i].trim().replace("<td>", "").replace("</td>", "")
//
//        crosswireModules[name] = description
//      } // for i
//    } // For each dataLocation.
//
//
//
//    /**************************************************************************/
//    //crosswireModules.keys.sorted().forEach { it -> Dbg.d(it + "\t" + crosswireModules[it] ) }
//    val moduleNameWithoutSuffix = ConfigData["stepModuleNameWithoutDisambiguation"]!!
//    m_DisambiguationSuffixForModuleNames = if (moduleNameWithoutSuffix in crosswireModules) suffix else ""
//    return m_DisambiguationSuffixForModuleNames!!
//  }