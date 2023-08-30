package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter




/*******************************************************************************/
object TestController
{
  const val C_SamiTest = true
}


/*******************************************************************************/
object SamiTestDetails
{
  /*****************************************************************************/
  fun createConfigDetails (mappings: List<String>): String
  {
    return """
<p><p><p>
=============================================================================
    """.trimIndent()
  }


  /*****************************************************************************/
  /* We give modules a variable suffix so that we can distinguish one version
     from another. */

  fun makeModuleNameSuffix (): String
  {
    m_ModuleNameSuffix = "Sami" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
    return m_ModuleNameSuffix
  }


  /*****************************************************************************/
  /* Copies the OSIS file to a Save folder under a name which ties it to the
     module. */

  fun saveOsisFile ()
  {
    val targetFolder = Paths.get(StandardFileLocations.getRootFolderName(), "_SavedOsis_")
    val osisFilePath = File(StandardFileLocations.getOsisFilePath())
    val osisCopyPath = File(osisFilePath.toString().replace(".xml", "$m_ModuleNameSuffix.xml"))
    osisFilePath.copyTo(osisCopyPath, overwrite = true)
    m_OsisFileUrl = "https://github.com/jamieStep/StepTextConversion/blob/main/Texts/Miscellaneous/en_NETSLXX/_SavedOsis_/${osisCopyPath.name}"
  }


  /*****************************************************************************/
  private var m_ModuleNameSuffix = ""
  private var m_OsisFileUrl = ""
}