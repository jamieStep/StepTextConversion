package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess


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
  var reportIfMissingDebugInfo: (key: String, type: String) -> Unit = ::reportMissingDebugInfoNull


  /****************************************************************************/
  /**
  * Checks that all mandatory values have been supplied, and that where
  * necessary they have non-blank values.
  */

  fun checkMandatories ()
  {
    var hadError = false

    m_Descriptors
      .filter { it.value.m_Required == Required.MandatoryAlways }
      .filter { (ConfigData.checkIfIsParameterKey(it.key)?.m_Value).isNullOrEmpty() }
      .forEach { hadError = true; val text = "Null / empty value not permitted for parameter ${it.key}."; Logger.error(text); Dbg.d(text) }

    m_Descriptors
      .filter { it.value.m_Required == Required.MandatoryAlwaysMayBeEmpty }
      .filter { null == ConfigData.checkIfIsParameterKey(it.key)?.m_Value }
      .forEach { hadError = true; val text = "Parameter ${it.key} must be defined."; Logger.error(text); Dbg.d(text) }

    if (hadError)
      throw StepExceptionWithStackTraceAbandonRun("Undefined or empty mandatory configuration parameters.")
  }


  /****************************************************************************/
  /**
  * Gets the descriptor for a given item as a string.
  *
  * @param key
  * @return Descriptor as string.
  */

  fun getDescriptorAsString (key: String) = getDescriptor(key)?.toString() ?: "[Description of parameter not found]"


  /****************************************************************************/
  /**
  * Initialises based upon command-line setting.
  *
  * @param dbgSetting Command-line setting.
  */

  fun initialise (dbgSetting: String?)
  {
    if (null == dbgSetting)
      return

    val dbgSettingLc = dbgSetting.lowercase()

    if ("generateconfig" in dbgSettingLc)
    {
      generateOutlineStepConfig("generateconfigall" in dbgSettingLc)
      exitProcess(0)
    }

    reportSet = if ("reportset" in dbgSetting.lowercase()) ::reportSet else ::reportSetNull
    reportIfMissingDebugInfo = if ("reportmissingdebuginfo" in dbgSettingLc) ::reportMissingDebugInfo else ::reportMissingDebugInfoNull
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun addDetails (line: String)
  {
    if (line.startsWith("#"))
      m_Comments.add("#!" + line.substring(2))
    else
    {
      val x = (line + "\t\t\t\t\t\t\t\t\t").split("\t")
      m_Descriptors[x[0]] = Descriptor(x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8])
    }
  }


  /****************************************************************************/
  private fun generateOutlineStepConfig (all: Boolean)
  {
    /**************************************************************************/
    fun outputChunk (details: Pair<String, Descriptor>)
    {
      val name = details.first
      val descriptor = details.second
      val notes = if (descriptor.m_Notes.isEmpty()) "" else (" " + descriptor.m_Notes)
      val options = if (descriptor.m_Options.isEmpty()) "" else (" Options: " + descriptor.m_Options)
      var default = if (descriptor.m_Default.isEmpty()) "" else (" Defaults to " + descriptor.m_Default)
      if (default.isNotEmpty() && !default.endsWith(".")) default += "."
      println("$name#=%%%${descriptor.m_Required}  #!$options$default ${descriptor.m_Description}$notes")
    }



    /**************************************************************************/
    var entries = m_Descriptors.entries
      .filter { ChangeWhen.Calculated != it.value.m_ChangeWhen }
      .map { Pair(it.key, it.value) }

    if (!all)
      entries = entries.filter { it.second.m_ChangeWhen.commonlyUsed }

    entries = entries.filter { WhereSet.File == it.second.m_WhereSet || WhereSet.FileOrCalculated == it.second.m_WhereSet }

    val groupedEntries = entries.groupBy { it.second.m_CollectionNo }



    /**************************************************************************/
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yy"))



    /**************************************************************************/
    val requestDetails = if (all)
      "This includes details of all config parameters which would normally be set in files.  Some of these are, in fact, rarely changed."
    else
      "You have asked me to show only the most commonly-used parameters.  Bear in mind that there are others which are used less frequently."

    println("Instructions")
    println("============")
    println()
    println(
"""
      The following is an outline configuration file (step.conf).
      
      $requestDetails 
      
      There are also other settings which can be made / need to be made on the command line or in the environment variable stepTextConverterParameters.
      
      The data for the file starts with the first line '#!!! ...' below and runs to the end of the output.  Any blank lines, and anything after '#!' is
      ignored when the configuration file is processed.  You can therefore add blank lines and comments as you see fit.
      
      Each non-comment line defines a single parameter.  It uses a 'forcible' definition (ie one using '#=' rather than just '='), so that the definition
      you give here will override any defaults the converter may supply.
      
      The right-hand side of each definition starts '%%%' and then indicates whether giving a value is optional, mandatory, etc.  I use the '%%%' to check
      you haven't forgotten anything.  If the processing finds a value which starts '%%%' I assume you've forgotten to fill in the actual value.
      
      You need to replace the '%%%...' by an actual non-blank value for anything marked as Mandatory.  Where things are marked as MandatoryOrBlank, you can
      give a non-blank value, or you can leave the value blank, which you do using a definition like 'thing#='.  Things marked MandatorySometimes are
      mandatory for certain types of text or run, and can be omitted otherwise.
      
      Optional values are just that -- you can supply a value (including a blank value) but you don't have to.
      
      If you don't wish to set a particular value, either delete that line from the file, or else comment it out by placing '#!' at the beginning.
      
      The file below ends with a standard ${'$'}include statement, which all step.conf files need to include, either directly or via other files.
       
      Do remember that where a number of different texts use the same information (for example, where a number of texts come from Biblica, and therefore
      have the same copyright owner details, you don't have to duplicate that information in each step.conf file: you can always put the data into a
      shared file which all the individual step.conf files include.      
      
      
    """.trimIndent())



    /**************************************************************************/
    println("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    println("#!")
    println("#! step.config created $today.")
    println("#!")
    print("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")


    /**************************************************************************/
    groupedEntries.forEach {
      println()
      println()
      println(m_Comments[it.key])
      it.value.forEach(::outputChunk)
    }


    /**************************************************************************/
    println(); println(); println()
    println("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    println("\$include \$jarResources/commonRoot.conf")
    println()



    /**************************************************************************/
    exitProcess(0)
  }


  /****************************************************************************/
  private fun getDescriptor (key: String): Descriptor? = m_Descriptors[key] ?: getSpecialDescriptor(key)


  /****************************************************************************/
  /* Gets descriptor information for keys where we don't have any specific
     information, but instead go with the prefix of the key -- eg V). */

  private fun getSpecialDescriptor (key: String): Descriptor?
  {
    val entry = m_SpecialDescriptors.keys.firstOrNull { key.startsWith(it) } ?: return null
    return m_SpecialDescriptors[entry]
  }



  /****************************************************************************/
  /* The first 'if' test below caters for the situation where we are attempting
     a Get.  When doing Get on something like @(stepVersificationScheme, KJV),
     "KJV" is passed to the ConfigData 'get' method (from which this present
     method is called) as though it were the name of a configuration
     parameter.  However, clearly here it is not -- it's a default value.
     We therefore need to avoid reporting that we lack configuration
     information for it.  I just hope that this test isn't too strong, and
     prevents lots of things from being checked. */

  private fun reportMissingDebugInfo (key: String, type: String)
  {
    if ('G' == type[0] && null == ConfigData.checkIfIsParameterKey(key))
      return

    if (null != getDescriptor(key)) return
    val text = "$type: Missing configuration debug information for $key."
    Logger.warning(text)
    Dbg.d(text)
  }


  /****************************************************************************/
  private fun reportMissingDebugInfoNull (key: String, type: String) {}


  /****************************************************************************/
  private fun reportSet (key: String, value: String?, location: String, additionalInfo: String?)
  {
    val descriptor = getDescriptor(key)
    val text = "ConfigData.set $key = ${value ?: "null"} at $location${if (null == additionalInfo) "" else " ($additionalInfo)"}."
    Logger.info(text)
    Dbg.d(text)
  }


  /****************************************************************************/
  private fun reportSetNull (key: String, value: String?, location: String, additionalInfo: String?) {}


  /****************************************************************************/
  private enum class ChangeWhen (val commonlyUsed: Boolean) { PerRunIfNecessary(true), PerRunForDebugging(false), PerTextAlways(true), PerTextIfAvailable(true), PerTextIfNecessary(true), PerTextIfWarranted(true), PerTextIfDesired(true), IfYouOptToUseThis(false), PerhapsPerLanguage(false), IfComputingEnvironmentChanges(false), IfCrosswireInternalsChange(false), IfOutsideWorldChanges(false), ProbablyNever(false), Calculated(false), NA(false),  }
  private enum class HowSet { Calculated, UserSpecified, UserSpecifiedOrCalculated, NA }
  private enum class Required { Calculated, MandatoryAlways, MandatoryAlwaysMayBeEmpty, MandatorySometimes, Optional, OptionalOrCalculated, NotUsed, NA }
  private enum class WhereSet { CommandLine, EnvironmentVariable, File, FileOrCalculated, Calculated, Anywhere, NA }


  /****************************************************************************/
  private class Descriptor (description: String,howSet: String,required: String,default: String,options: String,whereSet: String, changeWhen: String, notes: String)
   {
    val m_ChangeWhen: ChangeWhen = ChangeWhen.valueOf(changeWhen)
    val m_CollectionNo = m_Comments.size - 1
    val m_Default = default
    val m_Description: String = description
    val m_HowSet: HowSet = HowSet.valueOf(howSet)
    val m_Notes: String=notes
    val m_Options = options
    val m_Required: Required = Required.valueOf(required.ifEmpty { "NA" })
    val m_WhereSet: WhereSet = WhereSet.valueOf(whereSet.ifEmpty { "NA" })

    override fun toString () = "$m_Description | Set by ${m_HowSet.name} | Required: ${m_Required.name} | Default: $m_Default | Options: $m_Options | Set via ${m_WhereSet.name}${if (m_Notes.isEmpty()) "" else " $m_Notes"}."
  }

  private val m_Comments: MutableList<String> = mutableListOf()
  private val m_Descriptors: MutableMap<String, Descriptor> = mutableMapOf()
  private val m_SpecialDescriptors: MutableMap<String, Descriptor> = mutableMapOf()



  /****************************************************************************/
  init {
    FileLocations.getInputStream(FileLocations.getConfigDescriptorsFilePath()).first!!.bufferedReader().use { it.readText() } .lines() .forEach {
      val line = it.trim()
      if (line.isNotEmpty() && !line.startsWith("#!"))
        addDetails(line)
    }

    m_SpecialDescriptors["History_"] = Descriptor("History lines.", "Calculated", "Calculated", "", "", "", "Calculated", "")
    m_SpecialDescriptors["stepHistory_"] = Descriptor("History lines.", "Calculated", "Calculated", "", "", "", "Calculated", "")
    m_SpecialDescriptors["V_"] = Descriptor("Pieces of text, or fragments such as '[...]', of which a vernacular form may be desirable.", "UserSpecified", "MandatoryAlways", "Defaults to English version, which is built in to the converter.", "", "File", "PerhapsPerLanguage", "")
    m_SpecialDescriptors["swordDistributionLicence_"] = Descriptor("Standard rubrics describing the various typed of copyright (eg CC_BY_SA_4.0).", "UserSpecified", "MandatoryAlways", "", "", "File", "ProbablyNever", "All of the most likely values are already built into a file in the Resources section of the JAR file.")
    //m_SpecialDescriptors["stepNonOsisXsltStylesheet_"] = Descriptor("Stylesheets to be applied to a particular non-OSIS books before the main processing occurs.", "UserSpecified", "Optional", "", "", "File", "PerTextIfNecessary", "")
  }
}