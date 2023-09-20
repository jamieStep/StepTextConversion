/*******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


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
 *   - Returning a date/time-stamped prefix which can be used on module names
 *     etc in case more than one version is lying around.
 *
 *   - Creating a few lines of HTML which can be added to the Sword
 *     configuration file, for example to point to the files upon which the
 *     module was based.
 *
 *   - Saving intermediate files, such as the OSIS file, to a separate
 *     folder so that they continue to be available even after the 'main'
 *     version has been overwritten by later runs.  (These can be flagged with
 *     the same date/time-stamped prefix used to distinguish different
 *     versions of the module.)
 *
 *
 * To control this, look for the line marked with plus signs in TestController.
 * If you are applying a particular test, you need to set m_TestController to
 * be an appropriate instance of a class derived from TestControllerBase.  If
 * you do _not_ wish to apply tests (ie if you just want a plain vanilla run
 * of the system, comment out the line which assigns to m_TestController.
 *
 * You can use SamiTestController as an exemplar of a test class.
 *
 * @author ARA Jamieson
 */





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                              Initialisation                              **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/
object TestController: TestControllerBase()
{
  override fun atEndOfProcessing () { m_TestController.atEndOfProcessing() }

  override fun getTestName(): String { return m_TestController.getTestName() }

  override fun initialise () { } // Deliberately left blank.
  override fun makeTestRelatedDataForUseInConfigAbout () { m_TestController.makeTestRelatedDataForUseInConfigAbout() }

  private var m_TestController = TestControllerBase() // TestControllerBase is a null implementation, so unless this is overridden in the init block, the TestController does nothing.
  init
  {
    //m_TestController = SamiTestController // ++++++++++++ Change as per the kind of test to be applied, or comment out if not performing tests.
    m_TestController.initialise()
  }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                      Base class and null interface                       **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/
open class TestControllerBase
{
  /*****************************************************************************/
  private var m_UniquePrefix = ""


  /*****************************************************************************/
  /**
   * Saves any intermediate files.
   */
  open fun atEndOfProcessing () {}


  /*****************************************************************************/
  /**
   * Returns a name by which all of these tests can be identified.  This is used
   * at the front of file-name prefixes, so that related files can be grouped
   * together.
   *
   * @return Name
   */
  open fun getTestName (): String
  {
    return ""
  }


  /*****************************************************************************/
  /**
   * Initialisation.
   */
  open fun initialise ()
  {
    ConfigData.put("stepOsis2ModVariant", "Crosswire", true)
  }


  /*****************************************************************************/
  /**
   * Returns a date/time based prefix to be prepended to the module name so we
   * can tell one version from another.
   *
   * @return Prefix.
   */
  fun getModuleNamePrefix (): String
  {
    if (getTestName().isEmpty())
      return ""
    else
    {
      if (m_UniquePrefix.isEmpty()) m_UniquePrefix = getTestName() + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
      return m_UniquePrefix
    }
  }



  /*****************************************************************************/
  /**
   * Sets ConfigData stepAboutTestSupport in order to add test-related data to
   * the end of the Sword About information.
   */
  open fun makeTestRelatedDataForUseInConfigAbout (){ }



  /*****************************************************************************/
  /**
   * If this returns True, errors are converted to warnings.  This permits runs
   * to complete even if errors are detected.  (Except, of course, that
   * errors which are not acted upon may well mean that later processing goes
   * wrong.)
   *
   * @return True if errors are to be converted to warnings.
   */

   fun suppressErrors (): Boolean
   {
     return false
   }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                               Sami's stuff                               **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/
object SamiTestController: TestControllerBase()
{
  /*****************************************************************************/
  /**
   * Saves any intermediate files.
   */
  override fun atEndOfProcessing ()
  {
    File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).copyTo(File(m_JsonFileCopyLocalPath), overwrite = true)
    File(StandardFileLocations.getOsisFilePath()).copyTo(File(m_OsisFileCopyLocalPath), overwrite = true)
  }


  /*****************************************************************************/
  /**
   * Returns a name by which all of these tests can be identified.  This is used
   * at the front of file-name prefixes, so that related files can be grouped
   * together.
   *
   * @return Name
   */
  override fun getTestName (): String
  {
    return "Sami"
  }


  /*****************************************************************************/
  /**
  * Initialisation.
  */
  override fun initialise ()
  {
    ConfigData.put("stepOsis2ModVariant", "Step", true)
    makeGithubUrls()
    makeTestRelatedDataForUseInConfigAbout()
  }


  /*****************************************************************************/
  /**
  * Returns any additional information which should be added to the About field
  * of the Sword config file in support of testing.
  *
  * @return any details to be added.
 */
  override fun makeTestRelatedDataForUseInConfigAbout ()
  {
    val s = """
<p><p><p>
=============================================================================<p><p>
<div style='font-size:xx-large;color:red'>Supporting data</div>
<div>Tests: ${getTestName()}</div>
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

    m_JsonFileCopyLocalPath = Paths.get(saveFolder, getModuleNamePrefix() + File(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath()).name).toString()
    m_JsonCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_JsonFileCopyLocalPath).name}"

    m_OsisFileCopyLocalPath = Paths.get(saveFolder, getModuleNamePrefix() + File(StandardFileLocations.getOsisFilePath()).name).toString()
    m_OsisCopyFileGithubUrl = "$githubRoot$moduleParentFolderName/$moduleFolderName/$m_SaveFolderName/${File(m_OsisFileCopyLocalPath).name}"
  }


  /*****************************************************************************/
  private const val m_SaveFolderName = "_SavedTestData_"
  private var m_JsonFileCopyLocalPath = ""
  private var m_JsonCopyFileGithubUrl = ""
  private var m_OsisFileCopyLocalPath = ""
  private var m_OsisCopyFileGithubUrl = ""
}