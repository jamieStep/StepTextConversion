package org.stepbible.textconverter

import org.stepbible.textconverter.TextConverterProcessorEvaluateVersificationSchemes.evaluateSingleScheme
import org.stepbible.textconverter.support.bibledetails.BibleStructure
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
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
  * characters.
  */

  fun getMajorWarningsAsBigCharacters (): String
  {
    // The try below is required because if we start from OSIS, we won't have any reversification details.
    var res = ""
    try { if (!ConfigData["stepReversificationDataLocation"]!!.startsWith("http")) res += C_Local_ReversificationData } catch (_: Exception) {}
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) res += C_NotEncrypted
    if ("evalonly" == ConfigData["stepRunType"]!!.lowercase()) res += C_NonRelease
    return res
  }


  /****************************************************************************/
  /**
   * Returns details of any command-line parameters.
   *
   * @param commandLineProcessor Command line processor.
   */

  fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("updateReason", 1, "A reason for creating this version of the module (required only if runType is Release and the release arises because of changes to the converter as opposed to a new release from he text suppliers).", null, "Unknown", false)
  }


  /****************************************************************************/
  /**
  * Sets up portions of the environment which need to be sorted early on.
  */

  fun onStartup ()
  {
    determineOsis2modType()
    Osis2ModInterface.instance().initialise()
    determineModuleNameAudienceRelatedSuffix()
    determineModuleNameTestRelatedSuffix()
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
     STEPBible come down to the same thing.  A Bible is _sbOnly if:

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
        "_" + ConfigData["stepRunType"]!! + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
  }


  /****************************************************************************/
  private fun determineOsis2modType ()
  {
    /**************************************************************************/
    /* Type forced from command line? */

    val forcedOsis2modType = ConfigData["stepForceOsis2modType"]
    if (null != forcedOsis2modType)
    {
      ConfigData["stepOsis2modType"] = forcedOsis2modType.lowercase()
      return
    }



    /**************************************************************************/
    /* We need to use our own version unless we're very close to KJV(A).
       'Very close' here is rather an arbitrary threshold. */

    var apoc = if (BibleStructure.UsxUnderConstructionInstance().hasAnyBooksDc()) "A" else ""
    val schemeEvaluation = evaluateSingleScheme("KJV$apoc")!!
    ConfigData["stepOsis2modType"] =
      if (schemeEvaluation.booksMissingInOsis2modScheme > 0 || schemeEvaluation.versesMissingInOsis2modScheme > 10)
        "step"
      else
        "crosswire"
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