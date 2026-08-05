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

  val reportSet: (key: String, value: String?, location: String, additionalInfo: String?) -> Unit by lazy {
    val dbgSettingLc = ConfigData["stepDbgConfigData"]?.lowercase() ?: "???"
    if ("reportset" in dbgSettingLc) ::reportSet else ::reportSetNull
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
    /**************************************************************************/
    /* We can be confident that certain parameters will always be ok.  In
       particular, stepTextConverterSharedConfigurationDataRoot is ok, but
       validating it 'properly' puts things into a loop. */

    if ("stepTextConverterSharedConfigurationDataRoot" == parameterName)
      return true



    /**************************************************************************/
    fun initialiseValidationData ()
    {
      FileLocations.getInputStream(FileLocations.getConfigDescriptorsFileName()).first!!.bufferedReader().use { it.readText() } .lines() .forEach {
        val line = it.trim()
        if (line.isNotEmpty() && !line.startsWith("#!"))
          m_KnownParameters.add(line)
      }
    }



    /**************************************************************************/
    if (!m_InitialisedValidationData)
    {
      initialiseValidationData()
      m_InitialisedValidationData = true
    }



    /**************************************************************************/
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
  private var m_InitialisedValidationData = false


  /****************************************************************************/
  private val m_KnownParameters: MutableSet<String> = mutableSetOf()
  private var m_KnownStartingStrings = listOf("const", "History_", "V_", "stepNonOsisXsltStyleSheet_", "_")
}