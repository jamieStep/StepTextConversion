/*******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/******************************************************************************/
/**
 * Test controller.
 *
 * This has been developed specifically to cater for the investigations we
 * wished to apply when trying out various forms of reversification
 * processing -- although hopefully it will be of use elsewhere too.
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
 *     processing.)
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
 *     any outputs can be given unique names -- assuming, of course, that
 *     you'd find unique names useful.
 *
 *   - And you should override initialise, terminate and suppressErrors as
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
/******************************************************************************/
/**                                                                          **/
/**                               Base class                                 **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
open class TestController // Base class serves to support the Crosswire osis2mod.
{
  /****************************************************************************/
  open fun initialise () {}                           // What it says on the tin.
  open fun suppressErrors (): Boolean { return false} // Can be used to convert errors to warnings, thus, for example, preventing errors from causing files to be deleted.
  open fun terminate () {}                            // eg copy to another location any intermediate files of interest which might otherwise get lost on the next run.


  /****************************************************************************/
  /**
   * Returns a date/time based prefix to be prepended to the module name so we
   * can tell one version from another.  So long as makeUniqueNameForThisTest
   * has been set up to return a non-empty value, makes a name comprising that
   * value along with a date/time stamp.
   *
   * @return Prefix.
   */

  fun getUniqueIdentifierForRun (): String
  {
    return if (m_TestName.isEmpty()) "" else m_TestName + m_TimeStamp
  }


  /****************************************************************************/
  protected var m_TestName = ""
  private val m_TimeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")


  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /* Returns an instance of the actual controller in use. */

    fun instance (): TestController
    {
      if (null == m_Instance)
      {
        m_Instance = m_Options[ConfigData["stepTestType"]!!]
        m_Instance!!.initialise()
      }

      return m_Instance!!
    }


    /**************************************************************************/
    /**
     * Returns details of any command-line parameters this processor requires
     * or permits.
     *
     * @param commandLineProcessor Command line processor.
     */

    fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
    {
      commandLineProcessor.addCommandLineOption("testType", 1, "Type of test, if this is a test run", m_Options.keys.toList(), "NoTest", false)
    }


    /**************************************************************************/
    /* +++ Change m_Options as appropriate.  Make sure you always include
       NoTest. */

    private val m_Options = TreeMap<String, TestController>(String.CASE_INSENSITIVE_ORDER)
     .apply {
      put("Sami",TestControllerSami)
      put("CrossWireRelaxed", TestControllerCrosswireRelaxed)
      put("NoTest", TestControllerNoTest) // Always retain this entry.
    }

    private var m_Instance: TestController? = null
  }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                  Default set up when not running tests                   **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object TestControllerNoTest: TestController() // Ignore the fact that IDEA says this isn't used -- it is, but it's created using its name as a string.





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                            Relaxed Crosswire                             **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* This is basically a Crosswire run, but with most / all errors converted to
   warnings so they don't prevent us from generating a module. */

object TestControllerCrosswireRelaxed: TestController() // Ignore the fact that IDEA says this isn't used -- it is, but it's created using its name as a string.
{
  override fun suppressErrors (): Boolean { return true }
  init { m_TestName = "CWR" }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                               Sami's stuff                               **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/
object TestControllerSami: TestController() // Ignore the fact that IDEA says this isn't used -- it is, but it's created using its name as a string.
{
  /*****************************************************************************/
  /**
   * Saves any intermediate files.
   */

  override fun terminate ()
  {
    File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).copyTo(File(m_JsonFileCopyLocalPath), overwrite = true)
    File(StandardFileLocations.getOsisFilePath()).copyTo(File(m_OsisFileCopyLocalPath), overwrite = true)
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
<div>Tests: $m_TestName</div>
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

    m_JsonFileCopyLocalPath = Paths.get(saveFolder, getUniqueIdentifierForRun() + File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).name).toString()
    m_JsonCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_JsonFileCopyLocalPath).name}"

    m_OsisFileCopyLocalPath = Paths.get(saveFolder, getUniqueIdentifierForRun() + File(StandardFileLocations.getOsisFilePath()).name).toString()
    m_OsisCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_OsisFileCopyLocalPath).name}"
  }


  /*****************************************************************************/
  private const val m_SaveFolderName = "_SavedTestData_"
  private var m_JsonFileCopyLocalPath = ""
  private var m_JsonCopyFileGithubUrl = ""
  private var m_OsisFileCopyLocalPath = ""
  private var m_OsisCopyFileGithubUrl = ""


  /*****************************************************************************/
  init { m_TestName = "Sami" }
}