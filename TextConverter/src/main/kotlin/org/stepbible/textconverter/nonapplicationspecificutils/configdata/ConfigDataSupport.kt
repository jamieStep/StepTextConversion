package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun



/****************************************************************************/
/**
 * Provides support for debugging ConfigData activities.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigDataSupport: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Handlers. */

  var reportSet: (key: String, value: String?, location: String, additionalInfo: String?) -> Unit = ::reportSetNull


  /****************************************************************************/
  /**
  * Initialises based upon command-line setting.
  */

  fun initialise ()
  {
    val dbgSettingLc = ConfigData["stepDbgConfigData"]?.lowercase() ?: return
    reportSet = if ("reportset" in dbgSettingLc) ::reportSet else ::reportSetNull
  }


  /****************************************************************************/
  /**
  * Can be used to check that all parameter accesses are to parameters which
  * we know about -- the flexibility within the config facilities makes it
  * difficult to police things, and easy to introduce new parameter names
  * accidentally.
  *
  * @param parameterName Thing to check.
  * @param abort If true, aborts the run.  Otherwise returns true / false.
  * @return True if ok.
  */

  fun validateParameter (parameterName: String, action: String, abort: Boolean = true): Boolean
  {
    var res = false

    if (parameterName in m_KnownParameters)
      res = true
    else
      for (s in m_KnownStartingStrings)
        if (parameterName.startsWith(s))
        {
          res = true
          break
        }

    if (abort && !res)
      throw StepExceptionWithStackTraceAbandonRun("Attempt to $action unknown configuration parameter $parameterName.")
    else
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
  private fun reportSet (key: String, value: String?, location: String, additionalInfo: String?)
  {
    val text = "ConfigData.set $key = ${value ?: "null"} at $location${if (null == additionalInfo) "" else " ($additionalInfo)"}."
    Logger.info(text)
    Dbg.d(text)
  }


  /****************************************************************************/
  private fun reportSetNull (key: String, value: String?, location: String, additionalInfo: String?) {}


  /****************************************************************************/
  private val m_KnownParameters: MutableSet<String> = mutableSetOf()
  private var m_KnownStartingStrings = listOf("const", "History_", "V_", "stepNonOsisXsltStyleSheet_", "_")

  init {
    FileLocations.getInputStream(FileLocations.getConfigDescriptorsFilePath()).first!!.bufferedReader().use { it.readText() } .lines() .forEach {
      val line = it.trim()
      if (line.isNotEmpty() && !line.startsWith("#!"))
        m_KnownParameters.add(line)
    }
  }
}