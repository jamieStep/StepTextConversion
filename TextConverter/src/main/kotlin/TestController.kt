/*******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.File
import java.nio.file.Paths
import java.util.*


/******************************************************************************/
/**
 * Test controller.
 *
 * This has been developed specifically to cater for the investigations we
 * wished to apply when trying out various forms of reversification
 * processing -- although hopefully it will be of use elsewhere too.
 *
 * It is invoked only if testName has been passed on the command line, and does
 * not have the default value of NoTest.
 *
 * More particularly, this caters for ...
 *
 *   - Returning a date/time-stamped identifier which can be used on module
 *     names etc in case more than one version is lying around.
 *
 *   - Providing an opportunity to save intermediate and output files which
 *     would otherwise be overwritten on the next run.
 *
 *   - Suppressing the converter's normal error processing, so as to prevent
 *     premature termination, and also so as to ensure intermediate files are
 *     not deleted.  (Obviously this may not be entirely successful -- if the
 *     converter terminates prematurely, it will normally be because it
 *     suspects any errors it has detected are likely to invalidate further
 *     processing, so if you inhibit termination, there's no guarantee that
 *     the further processing will make sense.)
 *
 *
 * To use the features here ...
 *
 *   - You need to set up classes in support of each of the various tests
 *     you wish to run.  All should have names of the form TestControllerXxx,
 *     and all should inherit from TestControllerBase.  You must also have a
 *     TestControllerNoTest, which organises things for a non-test run.
 *
 *   - You need to modify m_Options (marked '+++' below), so that it maps
 *     the 'Xxx' part of all of these class names to the relevant test object.
 *     The names are not case-sensitive.
 *
 *   - Each inheriting class (with the exception of TestControllerNoTest)
 *     should probably set m_TestName as part of its initialisation, so that
 *     any outputs can be given names which reflect the name of the test.
 *
 *   - And you should override 'initialise', 'terminate' and 'suppressErrors' as
 *     appropriate.  This means, for instance, that you can arrange to
 *     copy intermediate files to some Save folder for later investigation,
 *     create information to be added to the Sword About field to identify
 *     the test, etc.  See TestControllerSami below (assuming it's still
 *     there) for an example.
 *
 *
 * To access the controller use TestControllerBase.instance().
 *
 * @author ARA Jamieson
 */

/******************************************************************************/
object TestController: TextConverterProcessor
{
  /****************************************************************************/
  override fun banner () = ""
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) = commandLineProcessor.addCommandLineOption("runType", 1, "Type of run.", getTestTypes(), "EvaluationOnly", false)
  override fun process () {}


  /****************************************************************************/
  override fun prepare ()
  {
    when (ConfigData["stepRunType"]!!.lowercase())
    {
      "release"      -> { ConfigData["stepReleaseType"] = "tbd"  ; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      "majorrelease" -> { ConfigData["stepReleaseType"] = "major"; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      "minorrelease" -> { ConfigData["stepReleaseType"] = "minor"; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      else           -> { val x = ConfigData["stepRunType"]!!.lowercase(); ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = x }
    }
  }


  /****************************************************************************/
  fun setInstance ()
  {
    val runType = ConfigData["stepRunType"]!!
    m_Instance = m_Options[runType]!!
    m_Instance!!.initialise()
  }


  /****************************************************************************/
  /* Returns an instance of the actual controller in use. */

  fun instance (): TestControllerSpecialisation = m_Instance!!


  /****************************************************************************/
  /**
   * Returns the names of the available tests.
   *
   * @return Names of available tests
   */

  fun getTestTypes (): List<String> = m_Options.keys.toList()


  /****************************************************************************/
  /* +++ Change m_Options as appropriate.  Make sure you always include
     Release. */

  private val m_Options = TreeMap<String, TestControllerSpecialisation>(String.CASE_INSENSITIVE_ORDER)
   .apply {
      put("Release", TestControllerRelease)         // Always retain this entry.
      put("MajorRelease", TestControllerRelease)    // Always retain this entry.
      put("MinorRelease", TestControllerRelease)    // Always retain this entry.
      put("EvalOnly", TestControllerEvaluationOnly) // Always retain this entry.
      put("EvaluationOnly", TestControllerEvaluationOnly) // Always retain this entry.
      put("Sami",TestControllerSami)
      put("CrosswireRelaxed", TestControllerCrosswireRelaxed)
  }

  private lateinit var m_Instance: TestControllerSpecialisation
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                             Derived classes                              **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

open class TestControllerSpecialisation
{
  open fun initialise () {}          // What it says on the tin.
  open fun suppressErrors () = false // Can be used to convert errors to warnings, thus, for example, preventing errors from causing files to be deleted.
  open fun terminate () {}           // eg copy to another location any intermediate files of interest which might otherwise get lost on the next run.
}


/******************************************************************************/
object TestControllerRelease         : TestControllerSpecialisation()            // Release run.
object TestControllerEvaluationOnly  : TestControllerSpecialisation()             // Module evaluation only.
object TestControllerSami            : TestControllerSavingFiles()  // Version for testing Sami's software.




/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                            Relaxed Crosswire                             **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* This is basically a Crosswire run, but with most / all errors converted to
   warnings so they don't prevent us from generating a module.  Except, of
   course, that when errors are raised, it's normally for a reason, and if we
   ignore them, there are likely to be consequences. */

object TestControllerCrosswireRelaxed: TestControllerSpecialisation() // Ignore the fact that IDEA says this isn't used -- it is, but it's created using its name as a string.
{
  override fun suppressErrors (): Boolean { return true }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**   Version which saves selected files and also adds information to the    **/
/**                              copyright page.                             **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
open class TestControllerSavingFiles: TestControllerSpecialisation() // Ignore the fact that IDEA says this isn't used -- it is, but it's created using its name as a string.
{
  /*****************************************************************************/
  /**
   * Saves any intermediate files.
   */

  override fun terminate ()
  {
    File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).copyTo(File(m_JsonFileCopyLocalPath), overwrite = true)
    File(StandardFileLocations.getInternalOsisFolderPath()).copyTo(File(m_OsisFileCopyLocalPath), overwrite = true)
  }


  /*****************************************************************************/
  /**
  * Initialisation.
  */
  override fun initialise ()
  {
    makeGithubUrls()
    createTestRelatedSwordAboutDetails()
  }


  /*****************************************************************************/
  /**
  * Returns any additional information which should be added to the About field
  * of the Sword config file in support of testing.
  *
  * @return any details to be added.
  */

  private fun createTestRelatedSwordAboutDetails ()
  {
    val s = """
<p><p><p>
=============================================================================<p><p>
<div style='font-size:xx-large;color:red'>Supporting data</div>
<div>Tests: Sami</div>
<div><a href='$m_OsisCopyFileGithubUrl' target='_blank'>OSIS</a></div>
<div><a href='$m_JsonCopyFileGithubUrl' target='_blank'>JSON file including mappings</a></div>
""".trimIndent()

    ConfigData.put("stepAboutTestSupport", s, true)
  }


  /*****************************************************************************/
  private fun makeGithubUrls ()
  {
    val githubRoot = "https://github.com/jamieStep/StepTextConversion/blob/main/Texts/"
    val saveFolder = Paths.get(StandardFileLocations.getRootFolderPath(), m_SaveFolderName).toString()
    val moduleFolderName = File(StandardFileLocations.getRootFolderPath()).name // eg en_NETSLXX.
    val moduleParentFolderName = File(StandardFileLocations.getRootFolderPath()).parentFile!!.name // eg 'Miscellaneous'.
    val moduleName = ConfigData["stepModuleName"]

    m_JsonFileCopyLocalPath = Paths.get(saveFolder, moduleName + File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).name).toString()
    m_JsonCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_JsonFileCopyLocalPath).name}"

    m_OsisFileCopyLocalPath = Paths.get(saveFolder, moduleName + File(StandardFileLocations.getInternalOsisFolderPath()).name).toString()
    m_OsisCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_OsisFileCopyLocalPath).name}"
  }


  /*****************************************************************************/
  private val m_SaveFolderName = "_SavedTestData_"
  private var m_JsonFileCopyLocalPath = ""
  private var m_JsonCopyFileGithubUrl = ""
  private var m_OsisFileCopyLocalPath = ""
  private var m_OsisCopyFileGithubUrl = ""
}
