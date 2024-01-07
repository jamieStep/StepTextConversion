package org.stepbible.textconverter.support.commandlineprocessor

import org.apache.commons.cli.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.stepexception.StepException
import java.util.*
import kotlin.system.exitProcess

/******************************************************************************/
/**
 * Does what it says on the tin.
 *
 * @author ARA "Jamie" Jamieson
 */

object CommandLineProcessor
{
    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                 Public                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    private data class CommandLineOption (val name: String, val nArgs: Int, val description: String, val options: List<String>?, val default: String?, val required: Boolean)


    /****************************************************************************/
    /**
     * Adds a command line option, avoiding duplication and beefing up the
     * description where possible.
     *
     * @param name Name of option.
     * @param nArgs Number of arguments required for this option.
     * @param description Description used in help information.
     * @param options Permitted options.
     * @param default Default value.
     * @param required True if parameter is mandatory.
     */

    fun addCommandLineOption (name: String, nArgs: Int, description: String, options: List<String>?, default: String?, required: Boolean)
    {
        if (m_CommandLineOptions.containsKey(name)) return

        var desc = description
        if (null != options) desc += "  Options: " + options.joinToString(" / ") + "."
        if (null != default) desc += "  Default: $default."
        val clo = CommandLineOption(name, nArgs, desc, options, default, required)

        m_CommandLineOptions[name] = clo // Add raw details to our own structure.

        val optBuilder = Option.builder(clo.name).desc(clo.description)
        if (clo.nArgs > 0) optBuilder.numberOfArgs(clo.nArgs) else optBuilder.hasArg(false)
        if (clo.required) optBuilder.required()
        m_Options.addOption(optBuilder.build())
    }


    /****************************************************************************/
    /**
     * Copies command line data to the configuration data, so that it is
     * available in a manner consistent with everything else.
     *
     * @param jarFileName Name of containing JAR file.
     */

    fun copyCommandLineOptionsToConfigData (jarFileName: String)
    {
        /************************************************************************/
        if (m_ParsedCommandLine!!.hasOption("help"))
        {
          showHelp(jarFileName)
          exitProcess(0)
        }



       /************************************************************************/
       fun recordOption (opt: Option)
       {
         val value = if (opt.hasArg()) opt.value else "y"
         ConfigData.delete(generateConfigDataName(opt.opt))
         ConfigData.put(generateConfigDataName(opt.opt), value, true)
       }



       /************************************************************************/
       /* Record defaults throughout, but don't force things -- we want to
          retain any settings from the configuration data in preference to the
          defaults. */

       m_CommandLineOptions.keys.filter { null != m_CommandLineOptions[it]!!.default } .forEach { ConfigData.put(generateConfigDataName(it), m_CommandLineOptions[it]!!.default!!, false) } // Assume defaults throughout, but don't use the 'force' facility  This means that all can be overridden.



       /************************************************************************/
       /* For things actually supplied on the command line, force the setting. */

       m_ParsedCommandLine?.options?.forEach { recordOption(it) } // Override with values actually supplied.
    }


    /****************************************************************************/
    /**
     * Gets a single option value.  Defaults are taken from the data used to
     * set up the command line, and validation is applied where appropriate.
     *
     * @param optionName Name of option as typed by user.
     *
     * @return Option value
     */

    fun getOptionValue (optionName: String): String?
    {
        /************************************************************************/
        /* Try getting a value actually supplied.  If there is none, return
           whatever we're holding as the default, and we assume that no
           validation need be applied to the default. */

        var res = m_ParsedCommandLine?.getOptionValue(optionName, null)
        if (null == res)
        {
            val opt = m_CommandLineOptions[optionName]
            if (opt?.default != null)
                res = opt.default
            return res
        }

        val options = m_CommandLineOptions[optionName]?.options
        if (null != options)
        {
            val found = options.any { res.equals(it, ignoreCase = true) }
            return if (found) res else throw StepException("Invalid value for command-line parameter $optionName: $res")
        }

        return res
    }


    /****************************************************************************/
    /**
     * Gets a single option value, with no defaulting and no error checking.
     *
     * @param optionName Name of option as typed by user.
     *
     * @return Option value
     */

    private fun getOptionValueRaw (optionName: String): String?
    {
        return m_ParsedCommandLine?.getOptionValue(optionName, null)
    }


    /****************************************************************************/
    /**
     * Gets a single option value, converted to lower case.  If the option value
     * would be null, an empty string is returned.
     *
     * @param optionName Name of option as typed by user.
     *
     * @return Option value, lower case.
     */

    fun getOptionValueLowerCase (optionName: String): String
    {
        val res = getOptionValueRaw(optionName)
        return res?.lowercase() ?: ""
    }



    /****************************************************************************/
    /**
     * Parses the command line.  If successful, the method squirrels away the
     * input details for later use.  If unsuccessful, it returns the usage
     * string.
     *
     * @param args Command-line arguments.
     * @param jarFileName The containing jar file.
     *
     * @return True if parse is successful.
     */

    fun parse (args: Array<String>, jarFileName: String): Boolean
    {
        return try
        {
            m_ParsedCommandLine = m_Parser.parse(m_Options, args)
            true
        }
        catch (e: ParseException)
        {
            showHelp(jarFileName)
            false
        }
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                 Private                                **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /* On the command line, it's not really natural to force all parameters to
       start 'step'.  Within the config data, however, all of mine do, so I
       need to be able to take the name of a command-line parameter and convert
       it to the config data form. */

    private fun generateConfigDataName (s: String): String
    {
      return "step" + s.substring(0, 1).uppercase() + s.substring(1)
    }


    /****************************************************************************/
    private fun showHelp (jarFileName: String)
    {
      val formatter = HelpFormatter()
      formatter.printHelp("java -jar $jarFileName.jar org.stepbible.textconverter.MainKt\n", m_Options)
      exitProcess(0)
    }


    /****************************************************************************/
    private val m_CommandLineOptions: MutableMap<String, CommandLineOption> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    private val m_Options = Options()
    private var m_ParsedCommandLine: CommandLine? = null
    private var m_Parser = DefaultParser()
}
