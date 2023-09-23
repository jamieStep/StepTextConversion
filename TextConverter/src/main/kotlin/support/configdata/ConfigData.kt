/******************************************************************************/
package org.stepbible.textconverter.support.configdata


import org.stepbible.textconverter.ReversificationData
import org.stepbible.textconverter.TestController
import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructureTextUnderConstruction
import org.stepbible.textconverter.support.bibledetails.BibleStructuresSupportedByOsis2modAll.canonicaliseSchemeName
import org.stepbible.textconverter.support.configdata.ConfigDataSupport.determineModuleDetails
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.iso.Unicode
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


/******************************************************************************/
/**
 * Reads, stores and makes available configuration data.
 *
 *
 *
 * ## Overview
 *
 * Configuration, it turns out, is both complex and very extensive -- because
 * the various texts potentially differ so much in their character, there are
 * many hundreds of aspects of the processing which we might wish to control
 * without necessarily wanting to change the Kotlin code.  Fortunately it does
 * not follow that we will often *need* to change the vast majority of
 * them; but there are quite a few which will *definitely* differ from one
 * text to another, and we require the flexibility to be able to change many
 * more should that turn out to be necessary.
 *
 * This implies a need to be able to set out a whole raft of defaults, and then
 * to override them as necessary.  We look at this in more detail shortly.
 *
 * A further complicating factor arises because the configuration data is not
 * entirely under our control.  Internally I use a simple home-brew format for
 * configuration data, partly for historical reasons, but also because it makes
 * it easy to override defaults in a fairly logical manner.
 *
 * DBL (the Digital Bible Library), on the other hand, normally makes a lot of
 * metadata available in XML form, and it is convenient to be able to pick up
 * some of the things we need from that without having to transcribe them --
 * which implies the need both to be able to handle XML inputs and to indicate
 * how to take this XML input and store it against the keys which otherwise
 * would be covered by the homebrew-format data.
 *
 *
 *
 *
 * ## General approach
 *
 * To a first approximation (weasel words to which I will return shortly), the
 * homebrew configuration information is made up of a series of key-value
 * pairs.
 *
 * The processing automatically sets up a lot of defaults on your behalf.  You
 * will also need, in your Metadata folder, a file called config.conf.  You
 * use this to define any parameters which absolutely _have_ to be set up on a
 * per-text basis (copyright information being an obvious example), or to
 * override the defaults.  You can put these definitions directly into
 * config.conf, or you can put them into other files which you refer to from
 * config.conf via an 'include' mechanism -- see below.
 *
 *
 *
 *
 *
 * ## The Include mechanism
 *
 * There are two forms of Include statement:
 *
 *     $include filePath
 *     $includeIfExists filePath
 *
 * Each must appear on a line in its own right in a config file, and each works
 * in the same way, the only difference being that the former terminates the
 * processing if the file does not exist, whereas the latter does not.
 *
 * The filePaths here are always *relative* paths.  They can include '.' and
 * '..' in the usual way, and the separators should be given as '/'.  The
 * processing supports a number of special forms:
 *
 * - $root at the start implies the path is relative to the root folder for the
 *   Bible text you are processing.
 *
 * - $metadata implies the path is relative to the Metadata folder for the
 *   text.
 *
 * - $common implies the file lies within the present JAR file, which is where
 *   I store a lot of defaults.  There is no level structure here (yet), so
 *   paths will always be of the form $common/fileName.
 *
 * - Anything else implies the path is relative to the path of the file in
 *   which the $include appears.
 *
 *
 * Include's may be nested to any depth.  Processing proceeds as though all of
 * the included files have been expanded out before it begins.
 *
 * Note, incidentally, that the processing makes no assumptions about the way
 * in which configuration information is, or is not, split across files.  It
 * requires that it *sees* the information in the appropriate order, not that
 * * it sees it in any particular file.  The only requirement is that there be
 * a config.conf file to act as a starting point.
 *
 *
 *
 *
 * ## The other bits
 *
 * Just to fill in the weasel words ('to a first approximation') above ...
 *
 * There are some values which we would like to regard as configuration items,
 * but which have to be determined at run time.  These are stored in the
 * configuration structure, and can indeed therefore be accessed as though they
 * were standard configuration items -- with the proviso that they become
 * available only once the processing needed to generate them has run.
 *
 * Command-line parameters are also stored in the configuration structure.
 * It is possible that configuration files may also attempt to give values to
 * these same parameters.  The values obtained from the command line always
 * take priority.
 *
 *
 *
 *
 *
 * ## Syntax -- overview
 *
 * Each line in a configuration file contains a comment, a definition, a
 * directive, or is blank.
 *
 * A single definition or directive may be continued over more than one line by
 * appending a backslash to the end of it.  Whitespace before a backslash is
 * retained.  Whitespace at the start of a continuation line is ignored.
 *
 *
 *
 *
 *
 * ## Syntax -- comments
 *
 * '#!' is used as a comment marker.  Comments may appear on lines in their own
 * right, or at the end of other lines.  The comment marker and anything to the
 * right of it, and any whitespace to the left of it, is removed before
 * processing begins.  If the result is a blank line -- or if you have any
 * lines which were entirely blank to begin with -- they are ignored.
 *
 * Note that a backslash at the end of a line containing a comment is regarded
 * as being part of the comment -- you can continue a line only if it does
 * *not* contain a comment.
 *
 *
 *
 *
 * ## Definition statements
 *
 * Definition statements are of the form:
 *
 *     key=value or
 *     key#=value
 *
 * The effect is to associate the given key with the given value.  If you
 * encounter two '=' settings for the same key, the later one takes
 * precedence.  Similarly if you have two '#=' settings.  If you have both a
 * '#=' and an '=' setting for the same key, the '#=' one takes precedence.
 *
 * This arrangement lets me start off by storing default settings, and then
 * giving the user the chance to override them.  '#=' gives you a way of
 * forcing a particular value to be used in preference to any 'normal'
 * definitions which will be encountered -- if it gives you a way of forcing
 * things.
 *
 * While reading the configuration data, these definitions are merely stored
 * against their associated key value.  They are evaluated only when something
 * asks to have the value associated with its key.
 *
 * Why does this matter?  Partly because definitions may refer to one another.
 * You can have eg
 *
 *      name=John
 *      .
 *      .
 *      .
 *      hi=Hello ${name}
 *
 * and hi will be given the value 'Hello John'.  By deferring evaluation until
 * the processing actually requires the data, I can cater for the possibility
 * that 'name' may have been defined more than once, and pick up the later
 * definition.  It also means that ordering doesn't matter -- the definition
 * for 'name' may come after that for 'hi' and it will still work.
 *
 * Definitions like this may be nested to any depth, so that you might, for
 * instance, have had:
 *
 *     name = ${firstName} ${surname}
 *
 * I recommend against doing anything too sophisticated, however, or it will
 * get very confusing.
 *
 * And the other reason for deferring things is that, as mentioned earlier,
 * there may be some cases where are dealing with files from a source such as
 * DBL, for which I have processing to read configuration data straight from
 * the metadata files they supply, and deferring makes this easier to handle in
 * a uniform manner.
 *
 *
 *
 *
 *
 * ## Special definition statements
 *
 * There are a few special cases:
 *
 * - There may be multiple lines each starting with the key
 *   vernacularBookDetails. These supply details of book names.  They are
 *   presently used internally only, so I won't go into detail here.
 *
 * - There may be multiple lines each starting with the key
 *   stepUsxToOsisTagTranslation. These are concerned with the (rather large
 *   number of) straightforward mappings from USX to OSIS tags.  More details
 *   are given in usxToOsisTagConversionsEtc.conf.
 *
 * - There may be lines starting $include.  These are directive, and are dealt
 *   with in the next section.
 *
 * - Some definition lines may include @getExternal on their right-hand sides.
 *   These pick up data from an external data source (of which currently only
 *   DBL is supported).  More information about this appears below.
 *
 *
 *
 *
 *
 * ## Directives
 *
 * The only directives currently supported are $include and $includeIfExists.
 * Unlike definition statements, these are actioned immediately they are
 * encountered.  The filePath part is subject to @(...) expansion in the
 * normal manner, but the evaluation occurs at the time the $include directive
 * is encountered, and not at the end of processing.  Note that one side-
 * effect of this is that any other definition to which you refer via ${...}
 * will itself also be evaluated at this point.
 *
 *
 *
 *
 * ## Getting data from external file formats
 *
 * There are some repositories (such as DBL) from which we obtain large numbers
 * of texts, and these (as in the case of DBL) may make metadata available in
 * their own standard form.
 *
 * If a given repository makes sufficient texts available, it may be worthwhile
 * creating special processing to pick up configuration data direct from that
 * repository's own metadata files, rather than have to transcribe it manually.
 * At present we have such processing for DBL (only); and you therefore need
 * to have available a file which explains how to extract the relevant
 * information from there.
 *
 * For details of this, see the file commonForSourceDbl.conf.  In essence, you
 * use special definition statements of the form:
 *
 *     key=@getExternal(metadata, ...)
 *
 * where 'metadata' can be any reasonable name you choose (ie there is no
 * particular significance to the actual name chosen), and serves as a logical
 * name for the file; and the '...' contains the parameters needed by the
 * processing to extract the relevant data from the external file.  (This syntax
 * has been developed purely to cater for the needs of DBL metadata at present,
 * but I'm reasonably hopeful it will carry through to other things, should we
 * ever decide to cater for them.)
 *
 * These definitions are subject to @(...) processing in the usual way, before
 * they are actioned, although I'd recommend not taking advantage of that.
 *
 * If you are going to use this feature, you also need one or more definitions
 * of the form:
 *
 *    stepExternalDataSource=dbl:metadata:$metadata/metadata.xml or
 *    stepExternalDataSourceIfExists=dbl:metadata:$metadata/metadata.xml
 *
 * to associate the external file with the logical name (so that in this case
 * the logical name 'metadata' is associated with $metadata/metadata/xml,
 * using the normal pathname conventions -- see the discussion of $include
 * above).  The 'dbl' portion identifies the type of the data (in this case
 * DBL, which is the only external format supported at the time of writing.
 *
 * The definition is not used until the first time a @getExternal statement is
 * encountered which uses that particular logical name, and it is the value at
 * that particular time which determines the file to be used.  Once a file has
 * been used for the first time, any later attempt to change the association
 * between logical name and file is ignored.
 *
 * You can have as many statements of this form as you need, so there is no
 * problem in picking up information from multiple different files if
 * necessary.  Also, there is nothing to prevent you from assigning two
 * different logical names with the same file, via two different
 * stepExternalDataSource statements.
 *
 * If you use the 'IfExists' form and the file does *not* exist, @getExternal
 * statements will return null.
 *
 * Much more information about the way in which configuration files are handled
 * appears in the header comments to the various default files themselves, so
 * I will not go into further detail here.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigData
{
    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                 Public                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    data class VernacularBookDescriptor
    (
        var ubsAbbreviation: String,
        var vernacularAbbreviation: String,
        var vernacularShort: String,
        var vernacularLong: String
    )





    /**************************************************************************/
    /**************************************************************************/
    /**                                                                      **/
    /**                              Data-load                               **/
    /**                                                                      **/
    /**************************************************************************/
    /**************************************************************************/

    /****************************************************************************/
    private val m_AlreadyLoaded: MutableSet<String> = mutableSetOf()
    private val m_Mandatories: MutableMap<String, Boolean> = mutableMapOf()
    private var m_SampleText = ""


    /****************************************************************************/
    /**
    * We need some sample text in order to be able to assess text direction when
    * it is not supplied overtly.  This enables the text to be set.
    *
    * @param text
    */

    fun setSampleText (text: String)
    {
      m_SampleText = text
    }


    /****************************************************************************/
    /**
    * We need some sample text in order to be able to assess text direction when
    * it is not supplied overtly.  This says whether we already have the text we
    * need.
    *
    * @return True if text is still needed.
    */

    fun wantsSampleText (): Boolean
    {
      return m_SampleText.isEmpty()
    }


    /****************************************************************************/
    /**
     * Loads metadata
     *
     * @param rootConfigFilePath The configuration file.  If the name starts
     *   '$common/', it is assumed that it names a file within the resources
     *   section of the present JAR file.  Otherwise, it is taken as being an
     *   actual path name.
     */

    fun load (rootConfigFilePath: String)
    {
        if (m_Initialised) return // Guard against multiple initialisation.
        m_Initialised = true
        determineModuleDetails()
        load(rootConfigFilePath, null, false) // User overrides.
        loadDone()
    }


    /****************************************************************************/
    /* Need this as a separate function because $include's involve recursive
       calls. */

    private fun load (configFilePath: String, callingFilePath: String?, okIfNotExists: Boolean)
    {
        /**************************************************************************/
        //Dbg.d("Loading config file: " + configFilePath)



        /**************************************************************************/
        val handlingEnglishTranslationsFile = configFilePath.endsWith("vernacularTextTranslations_eng.conf")
        if (handlingEnglishTranslationsFile)
          m_ProcessingEnglishMessageDefinitions = true



        /**************************************************************************/
        /* Originally I would have regarded any attempt to load the same file twice
           as probably indicating an error.  However, it is convenient to accept
           this and simply not load the file a second time.  (Slightly obscure
           rationale: this lets me create a bog standard base configuration file
           in the resources section and then copy this as the template config.conf
           file for each new text.  To minimise the number of changes required to
           that template file, it is convenient if the template file $include's
           itself, but I want the file to be included only once. */

        if (m_AlreadyLoaded.contains(configFilePath)) return
        m_AlreadyLoaded.add(configFilePath)



        /**************************************************************************/
        val lines = getConfigLines(configFilePath, callingFilePath, okIfNotExists)
        for (x in lines)
        {
            var line = x
            val lineLowerCase = line.lowercase().trim()

            line = line.replace("@home", System.getProperty("user.home"))

            if (lineLowerCase.startsWith("#vernacularbookdetails"))
            {
                processVernacularBibleDetails(line)
                continue
            }


            if (line.matches(Regex("(?i)stepUsxToOsisTagTranslation.*")))
            {
                saveUsxToOsisTagTranslation(line)
                continue
            }

            if (line.matches(Regex("(?i)stepExternalDataSource.*")))
            {
              ConfigDataExternalFileInterface.recordDataSourceMapping(line, configFilePath)
              continue
            }


            if (lineLowerCase.startsWith("\$include"))
            {
                var newFilePath = line.replace("${'$'}includeIfExists", "")
                newFilePath = newFilePath.replace("${'$'}include", "")
                newFilePath = expandReferences(newFilePath, false)!!
                load(newFilePath, configFilePath, lineLowerCase.contains("exist"))
                continue
            }



            /**************************************************************************/
            if (line.matches(Regex("(?i)copyAsIs.*")))
            {
              m_CopyAsIsLines.add(line.substring(line.indexOf("=") + 1))
              continue
            }



            /**************************************************************************/
            if (line.contains("=")) // I think this should account for all remaining lines.
            {
              line = line.replace("(?i)\\.totextdirection\\s*\\(\\s*\\)".toRegex(), ".#toTextDirection")
              line = line.replace("(?i)\\.todate\\s*\\(\\s*\\)".toRegex(), ".#toDate")
              loadParameterSetting(line)
            }
        } // for



        /**************************************************************************/
        /* Clear m_ProcessingEnglishDefinitions only if we're actually processing
           the English message file.  We can't clear it willy-nilly because it's
           always possible the English file may include other files of different
           names, but they'll all be contributing to the English definitions. */

        if (handlingEnglishTranslationsFile)
          m_ProcessingEnglishMessageDefinitions = false
    }


    /****************************************************************************/
    /* Obtains the configuration lines from a given file, which may be specified
       as being relative to the root folder, relative to the calling file (if
       supplied) or within the JAR.

       The returned list will have had comments and blank lines removed and
       continuation lines combined into a single line.

       I also replace *= by =.  *= was used under particular circumstances in the
       old config files.  I no longer need it, but I don't want to have to change
       all of the old files.
     */

    private fun getConfigLines (configFilePath: String, callingFilePath: String?, okIfNotExists: Boolean): List<String>
    {
        /************************************************************************/
        try
        {
            val rawLines = StandardFileLocations.getInputStream(configFilePath, callingFilePath)!!.bufferedReader().use { it.readText() } .lines()
            val lines: MutableList<String> = rawLines.map{ it.split("#!")[0].trim() }.filter{ it.isNotEmpty() }.toMutableList() // Ditch comments and remove blank lines.
            for (i in lines.size - 2 downTo 0) // Join continuation lines.
                if (lines[i].endsWith("\\"))
                {
                    lines[i] = lines[i].substring(0, lines[i].length - 1) + lines[i + 1].replace(Regex("^\\s+"), "")
                    lines[i + 1] = ""
                }

            return lines.filter { it.isNotEmpty() }
        }
        catch (_: Exception)
        {
            if (!okIfNotExists) throw StepException("Could not find config file $configFilePath")
            return listOf()
        }
    }


    /****************************************************************************/
    /**
     * Called when all defaults and metadata have been handled.
     * <p>
     *
     * The method also checks that mandatory parameters have been supplied, and
     * sets to an empty string any optional parameters which have not been
     * supplied.
     */

    private fun loadDone()
    {
        /**************************************************************************/
        /* Convert any saved tag translation details to usable form. */

        generateTagTranslationDetails()



        /**************************************************************************/
        /* Check that everything has been handled. */

        m_Mandatories.keys.filter { it !in m_Metadata } .forEach { Logger.error("No value supplied for mandatory parameter $it.")}
        m_Mandatories.keys
            .filter { !m_Mandatories[it]!! } // Not permitted to be empty.
            .filter { it in m_Metadata } // We do have an associated value.
            .filter { get(it)!!.isEmpty() } // But the value is empty.
            .forEach { Logger.error("Null / empty value not permitted for parameter $it.")}

    }


    /****************************************************************************/
    /* Parses a line so as to determine the key and value, and stores them. */

    private fun loadParameterSetting (line: String)
    {
        val force = line.contains("#=")
        val parts = line.split(Regex(if (force) "\\#\\=" else "\\="), 2)

        if (parts[1].startsWith("@Mandatory", ignoreCase = true))
          m_Mandatories[parts[0]] = parts[1].contains("MayBeEmpty", ignoreCase = true)
        else
          put(parts[0].trim(), parts[1].trim(), force)
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                              Get and put                               **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
     * Deletes a given key/value pair.  Intended mainly for use during command
     * line processing, where I need to be able to force things to their default
     * values, but then override these forced values with new forced values for
     * things which are not being defaulted.
     *
     * @param key
     */
    fun delete (key: String)
    {
      if (key in m_Metadata) m_Metadata.remove(key)
    }


    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or returns null if the key
    * has no associated value.
    *
    * @param key
    * @return Associated value or null.
    */

    operator fun get (key: String): String?
    {
      return getInternal(key, true)
    }


    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or returns the default if the
    * key has no associated value.
    *
    * @param key
    * @para dflt
    * @return Associated value or null.
    */

    fun get (key: String, dflt: String): String
    {
      return getInternal(key, true) ?: dflt
    }


    /****************************************************************************/
    /**
    * The configuration data may include lines which are simply to be copied
    * as-is to the Sword configuration file.  This returns that list.
    *
    * @return As-is lines.
    */
    fun getCopyAsIsLines (): List<String>
    {
      return m_CopyAsIsLines
    }



    /****************************************************************************/
    /**
    * Gets the value associated with a given key, or throws an exception if the
    * key has no associated value.
    *
    * @param key
    * @return Associated value if any.
    */

    fun getOrError (key: String): String
    {
      return get(key) ?: throw StepException("No metadata found for $key.")
    }



    /****************************************************************************/
    /**
    * Obtains the value associated with a given key, or throws an exception if
    * the key has no associated value.  The value is assumed to be a string
    * representation of a Boolean -- Y(es) or T(rue) or anything else.  Returns
    * true if the value starts with Y or T (not case-sensitive).
    *
    * @param key
    * @return True or false.
    */

    fun getAsBoolean (key: String): Boolean
    {
      var s = getOrError(key)
      if (s.isEmpty()) return false
      s = s.substring(0, 1).uppercase()
      return s == "Y" || s == "T"
    }


    /****************************************************************************/
    /**
    * Obtains the value associated with a given key, or uses a default if
    * the key has no associated value.  The value is assumed to be a string
    * representation of a Boolean -- Y(es) or T(rue) or anything else.  Returns
    * true if the value starts with Y or T (not case-sensitive).
    *
    * @param key
    * @param dflt Default value if key has no associated value.
    * @return True or false.
    */

    fun getAsBoolean (key: String, dflt: String): Boolean
    {
      val s = (get(key) ?: dflt).substring(0, 1).uppercase()
      return s == "Y" || s == "T"
    }


    /****************************************************************************/
    /**
     *  Checks if a given key corresponds to translatable text which has no
     *  vernacular override, and is therefore in English.
     *
     *  @return True if this is the English version of a piece of translatable
     *    text.
     */
    fun isEnglishTranslatableText (key: String): Boolean
    {
      return m_EnglishDefinitions.contains(key)
    }


    /****************************************************************************/
    /**
     * Stores a given key / value pair, or overwrites an existing one.
     *
     * @param key Key.
     * @param theValue Associated value.
     * @param force If true, later calls for this same key are ignored.
     *
     */

    fun put (key: String, theValue: String, force: Boolean)
    {
      /************************************************************************/
      //Dbg.d(key, "stepTextDirection")



      /************************************************************************/
      /* If this is a 'force' setting and we already have a force setting, we
         retain the existing one. */

      if (force && key in m_Metadata && m_Metadata[key]!!.m_Force)
        return



      /************************************************************************/
      /* If we're forcing, then it's always ok to write to the store because
         force overrides any existing non-force value.  If we're _not_ forcing,
         then it's ok to write only if an entry does not already exist, or if
         any existing entry was not forced.  In other words, in general,
         later wins. */

      if (!force)
      {
        val tmp = m_Metadata[key]  // If we're not forcing, it's ok to store the new data if either there's no existing entry, or the existing one is not marked force.
        if (null != tmp && tmp.m_Force) return
      }



      /************************************************************************/
      /* Sort out special markers. */

      var value = tidyVal(theValue)



      /************************************************************************/
      /* Get rid of parens and comma from @getExternal to simplify parsing
         later. */

      value = C_patGetExternalRaw.replace(value) { it.value.replace("(", "[").replace(")", "]").replace(",", " ") }



      /************************************************************************/
      /* This may be a bit flaky, but here goes ...

         We're concerned here only with translatable text strings (which we can
         recognise because their names start 'V_', and nothing else is supposed
         to do so).

         We have a set of English definitions which act as a default, which are
         used on English texts; but some or all of these may be overridden by
         vernacular translations on some texts.  And the aim here is to keep
         track of which definitions end up still in English, and which have been
         overridden.

         m_ProcessingEnglishDefinitions is set elsewhere to tell us whether we're
         processing the English definitions or not.  If we are, we add them to the
         list of English keys.  If we're not, we remove them from that list. */

      if (key.startsWith("V_"))
      {
        if (m_ProcessingEnglishMessageDefinitions)
          m_EnglishDefinitions.add(key)
        else
          m_EnglishDefinitions.remove(key)
      }



      /************************************************************************/
      m_Metadata[key] = ParameterSetting(value, force)
    }


    /****************************************************************************/
    /* This should be called only directly from the external-facing methods or
       when expanding @-references and we have a new value which needs to be
       looked up. */

    private fun getInternal (key: String, nullsOk: Boolean): String?
    {
      //Dbg.d(key, "stepBibleNameVernacular")

      val calculated = getCalculatedValue(key)
      if (null != calculated && "@get" !in calculated) return calculated

      val parmDetails = m_Metadata[key] ?: return null
      return expandReferences(parmDetails.m_Value, nullsOk)
    }


    /****************************************************************************/
    /**
    * Sets a value, without forcing.
    *
    * @param key
    * @param value
    */
    operator fun set (key: String, value: String)
    {
      put(key, value, false)
    }






    /**************************************************************************/
    /**************************************************************************/
    /**                                                                      **/
    /**                  Expansion of embedded references                    **/
    /**                                                                      **/
    /**************************************************************************/
    /**************************************************************************/

    /**************************************************************************/
     private val C_patGetExternalRevised = Regex("(?i)(?<pre>.*?)@getExternal\\[(?<selector>\\w+)\\W+(?<xpath>.+?)](\\.#(?<fn>\\w+))?(?<post>.*)")
     private val C_patGetExternalRaw = Regex("(?i)(@getExternal\\(.+?\\))")
     private val C_patAt = Regex("(?<pre>.*?)@\\((?<key>.+?)\\)(\\.to(?<fn>\\w+)\\(\\))?(?<post>.*)")


     /**************************************************************************/
     /**
      * Loops over a string repeatedly replacing @(...) entries by their
      * associated values until we no longer succeed in making any changes.
      * Mainly used internally, but also used when generating the Sword config
      * file.
      *
      * This is complicated, and I almost certainly haven't covered all nesting
      * possibilities (@getExternal within @(...) within @getExternal etc), but
      * hopefully it's good enough for the things we're likely to encounter.
      *
      * @param value Value to be expanded.
      * @return Expanded value.
      */

     fun expandReferences (value: String, nullIsOk: Boolean): String?
     {
       val errorStack: Stack<String> = Stack()
       errorStack.push(value)

       try
       {
         return expandReferences1(value, errorStack, if (nullIsOk) 999 else 1)
       }
       catch (_: Exception)
       {
         throw StepException("ConfigData error parsing: " + errorStack.joinToString(" -> "))
       }
     }



     /**************************************************************************/
     private fun expandReferences1 (theValue: String?, errorStack: Stack<String>, level: Int): String?
     {
         /**********************************************************************/
         //Dbg.dCont(theValue!!, "todate")



         /**********************************************************************/
         fun doAdditionalProcessing (mr: MatchResult, value: String?): String?
         {
           if (null == value) return null
           var x = value
           if (null != mr.groups["fn"]) x = specialistProcessing(value, mr.groups["fn"]!!.value)
           return mr.groups["pre"]!!.value + x + mr.groups["post"]!!.value
         }



         /**********************************************************************/
         if (null == theValue)
         {
           if (1 == level) throw StepException("")
           return null
        }



        /**********************************************************************/
        /* tidyVal (above) sorts out things like converting {space} to ' '.  We
           now replace escaped parens by special markers so they don't confuse
           parsing. */

        var value = tidyVal(theValue).replace("\\(", "\u0000").replace("\\)", "\u0001")



        /**********************************************************************/
        /* Expand all @(...)'s, one at a time. */

        while (true)
        {
          val mr: MatchResult = C_patAt.find(value) ?: break
          value = doAdditionalProcessing(mr, expandReferencesAt(value, mr, errorStack, level)) ?: return null
        }



         /**********************************************************************/
         /* Expand all @getExternal's, one at a time. */

         while (true)
         {
           val mr: MatchResult = C_patGetExternalRevised.find(value) ?: break
           value = doAdditionalProcessing(mr, expandReferencesGetExternal(value, mr, errorStack, level)) ?: return null
         }



       /**********************************************************************/
        value =  value.replace("\u0000", "(").replace("\u0001", ")")
        return value
     }


     /**************************************************************************/
     /* Expands all @getExternal's.  Because getExternal is concerned with data
        obtained from a third party who will not use our calling conventions, we
        can be confident that the data we obtain will not use @(...), and
        therefore we can be sure that no further expansion of the data is
        required. */

     private fun expandReferencesGetExternal (theValue: String, mr: MatchResult, errorStack: Stack<String>, level: Int): String?
     {
       var res = ConfigDataExternalFileInterface.getData(mr.groups["selector"]!!.value, mr.groups["xpath"]!!.value)
       if (null == res && 1 == level)
       {
         errorStack.push(theValue)
         throw StepException("")
       }

       if (null == res)
         return null

       //if (null != mr.groups["fn"] && mr.groups["fn"]!!.value.isNotEmpty())
       //  res = specialistProcessing(res, mr.groups["fn"]!!.value)

       return res + mr.groups["post"]!!.value
     }



     /****************************************************************************/
     /* Certain values have to be determined based upon other values, or else
        require to be converted to canonical form.  The processing here handles
        this.  Note that it is not concerned with expanding @-references or
        anything like that -- it works things out based purely upon its own
        knowledge. */

     private fun getCalculatedValue (key: String): String?
     {
         /************************************************************************/
         when (key.lowercase())
         {
           /**********************************************************************/
           /* We have language codes and language codes.  Crosswire wants
              extended information in the Sword configuration file -- the
              2-character language code (or the 3-character code if no 2-character
              code is available), followed by the script code (if any) and the
              country code (again if any), with dashes as separators.  And I think
              trailing dashes need to be stripped off.  I'm not sure what is
              supposed to happen if we have a country code but no script code;
              I guess that should never happen. */

           "stepextendedlanguagecode" ->
           {
             val languageCode = get("stepLanguageCode2Char")!!
             val script = get("stepSuppliedScriptCode")!!
             val country = get("stepSuppliedCountryCode")!!
             var res = listOf(languageCode, script, country).joinToString("-")
             while (res.endsWith("-")) res = res.substring(0, res.length - 1)
             return res
           }



           /**********************************************************************/
           /* This is fairly unpleasant, and will need careful maintenance (which
              means that at some point you will find yourself cursing me for
              having done this).  Certain aspects of the processing (most notably
              determining the full module name, which appears below) need to know
              whether STEP has done anything to add value to the module.  At the
              time of writing, value may be added by adding Strong's markup,
              morphology markup, or 'significant' reversification.  The first two
              have to be flagged directly in the config, but the third is
              calculated internally.  We cannot therefore give back a value for
              stepHasAddedValue until that assessment has been carried out, which
              can be recognised because only at that point will
              stepAddedValueReversification have been set.

              Note that this will need to be altered if the list of things via
              which we can add value is changed.  Look also for a reference to
              stepHasAddedValue in ConverterOsisToSwordController, because there is
              a following para which adds information about the various added value
              to the copyright page, and this will need changing too. */

            "stephasaddedvalue" ->
            {
              if (null == getInternal("stepAddedValueReversification", true))
                throw StepException("Metadata request for stepHasAddedValue before reversification has run")

              return if (getAsBoolean("stepAddedValueMorphology", "No") ||
                         getAsBoolean("stepAddedValueStrongs", "No") ||
                         getAsBoolean("stepAddedValueReversification")) "Yes" else "No"
            }



           /**********************************************************************/
           /* Also rather awkward.  DIB has asked that where STEP is adding value
              to a module (see previous para for details) we add a suffix to the
              module name.  We were able to set stepModuleNameWithoutSuffix early
              on, based purely upon looking at the name of the root folder, but we
              can't return a full module name (including possible suffix) until
              we've carried out an assessment as to whether reversification will
              need to be applied.

              Except, on 13-Apr-22, this changed rather: now in general we don't
              want to add this suffix after all, regardless of what we have done
              to the module, unless we reckon there is a risk of a name clash.
              This is controlled by stepDecorateModuleNamesWhereStepHasAddedValue.
            */

            "stepmodulename" ->
            {
              var moduleName = getInternal("stepModuleNameWithoutSuffix", false)
              if (getAsBoolean("stepHasAddedValue") && getAsBoolean("stepDecorateModuleNamesWhereStepHasAddedValue", "No")) moduleName += "_"
              moduleName = TestController.getModuleNamePrefix() + moduleName
              return moduleName
            }



           /**************************************************************************/
           "steptextdirection" ->
           {
             if (null != m_Metadata["stepTextDirection"]) return m_Metadata["stepTextDirection"]!!.m_Value
             return Unicode.getTextDirection(m_SampleText) // $$$ Need a string taken from a canonical portion of the scripture as input.
           }



           /**************************************************************************/
           "steptextdirectionforsword" ->
           {
             val x = getInternal("stepTextDirection", false)
             return if ("LTR" == x || "LtoR" == x) "LtoR" else "RtoL"
           }



           /**************************************************************************/
           "stepTextModifiedDate" ->
           {
             val modificationDate = m_Metadata["stepTextModifiedDate"]
             return if (null == modificationDate)
               "Text accessed: " + getInternal("stepVernacularTextAccessedDate", false)
             else
               "Text last modified: $modificationDate"
           }



           /**********************************************************************/
           /* I allow the configuration data to force verse-per-line.  The main
              rationale for this code paragraph, however, was that at one stage
              RTL texts had to be forced to verse-per-line or they were
              rendered incorrectly.  This is no longer the case. */

          "stepforceverseperline" ->
           {
             if (null != m_Metadata["stepForceVersePerLine"]) return m_Metadata["stepForceVersePerLine"]!!.m_Value
             // return if ("RTL" == getInternal("stepTextDirection", false)) "true" else "false"
             return "false"
           }



           /**********************************************************************/
           /* We need to be very careful to get the case right here, because this is
              usually passed as an argument to osis2mod.exe, and there the scheme
              name is case-sensitive. */

            "stepVersificationScheme" ->
            {
              val parmValue = m_Metadata[key] ?: throw StepException("No metadata found for $key.")
              var schemeName = parmValue.m_Value.lowercase()
              if (schemeName.startsWith("nrsv") || schemeName.startsWith("kjv"))
                schemeName = schemeName.substring(0, schemeName.length - 1) +
                  if (BibleStructureTextUnderConstruction.hasDc() || ReversificationData.targetsDc()) "a" else ""
              return canonicaliseSchemeName(schemeName)
            }



            /**********************************************************************/
            else ->
              return null
        } // when
     }


     /**************************************************************************/
     /* Applies any additional specialist processing required on certain types
        of value. */

     private fun specialistProcessing (value: String, fn: String): String
     {
       var revisedValue = value

       when (fn.lowercase())
       {
         "todate" ->
         {
           if (revisedValue.matches(".*([+-])\\d\\d:\\d\\d$".toRegex())) // Assume standard format but may need to get rid of daylight savings because LocalDateTime can't handle it.
             revisedValue = revisedValue.substring(0, revisedValue.length - 7)
           val ldt = LocalDate.parse(revisedValue)
           val cal = Calendar.getInstance()
           cal[ldt.year, ldt.monthValue - 1] = ldt.dayOfMonth
           revisedValue = SimpleDateFormat("dd-MMM-yyyy").format(cal.time)
         }

         "totextdirection" ->
         {
           revisedValue = revisedValue.lowercase()
           revisedValue = if (revisedValue == "ltr") "LtoR" else "RtoL"
         }
       } // when


       return revisedValue
     }


     /**************************************************************************/
     /* This is called to expand @(aaa, bbb, ... =xzy) where the =xyz is a fixed
        string to serve as the default and is optional, and there may be one or
        more other elements.

        This method deals with only a single @(...) as defined by the match
        result passed to it.  It works through each element aaa, bbb, ... in
        turn trying to obtain a value for it, which it does via a recursive
        call to expandReferences1.  If it fails, it falls back on any default
        instead.

        Note that there is no call to have another @(...) nested inside the one
        we are processing, which simplifies the parsing -- an element may
        _resolve_ to a string containing further @(...)'s, but that string is
        dealt with via the recursive call and therefore does not serve to
        confuse the present one. */

     private fun expandReferencesAt (theValue: String, mr: MatchResult, errorStack: Stack<String>, level: Int):  String?
     {
       /************************************************************************/
       var mainPart = mr.groups["key"]!!.value
       var dflt: String? = null
       //Dbg.dCont(theValue, "code3")



       /************************************************************************/
       /* '=' marks the start of the default value if any.  Split this off. */

       val ix = mainPart.indexOf("=")
       if (ix >= 0)
       {
         dflt = mainPart.substring(ix + 1)
         mainPart = mainPart.substring(0, ix)
       }



       /************************************************************************/
       /* Having got the default out of the way, the remainder of the @(...) may
          consist of one or more elements, comma-separated. */

       val parts = mainPart.split(",")
       var resolvedValue: String? = null

       for (i in parts.indices)
       {
         resolvedValue = expandReferences1(parts[i].trim(), errorStack, level + 1)
         if (null != resolvedValue)
         {
           if (!parts[i].lowercase().contains("@get"))
             resolvedValue = getInternal(resolvedValue, false)
         }

         if (null != resolvedValue)
           break
       }



       /************************************************************************/
       /* Default if necessary. */

      if (null == resolvedValue) resolvedValue = dflt
      if (null == resolvedValue && 1 == level)
      {
        errorStack.push(theValue)
        throw StepException("")
      }

      return resolvedValue
     }






    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                           Book descriptors                             **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * Returns a list of vernacular book descriptors, if these are known (an
    * empty list if not).
    *
    * @return Book descriptors.
    */

    fun getBookDescriptors(): List<VernacularBookDescriptor>
    {
        return m_BookDescriptors
    }


    /****************************************************************************/
    /* Book details need to be of the form:

         #VernacularBookDetails usxAbbrev:=abbr:=abc;short=abcd;long=abcde

       where there is no _need_ to have more than one of the abbr/short/long.
    */

    private fun processVernacularBibleDetails (theLine: String)
    {
        var line = theLine.replace("#VernacularBookDetails", "").trim()

        val bd = VernacularBookDescriptor("", "", "", "")

        val ix = line.indexOf(":")
        bd.ubsAbbreviation = line.substring(0, ix).trim()

        line = line.substring(ix + 2)
        val names = line.split(";")

        for (nameDetails in names)
        {
            val elts = nameDetails.split(":")
            val length = elts[0].trim().lowercase()
            val text = elts[1].trim()
            when (length)
            {
                "abbr"  -> bd.vernacularAbbreviation = text
                "short" -> bd.vernacularShort = text
                "long"  -> bd.vernacularLong = text
            }
        }

        m_BookDescriptors.add(bd)
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                          Miscellaneous gets                            **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * Does what it says on the tin.  Intended presently only for use by the
    * various reference readers and writers.
    *
    * @param prefix The prefix for configuration parameters.
    */

    fun getValuesHavingPrefix (prefix: String): Map<String, String?>
    {
        val res: MutableMap<String, String?> = HashMap()
        m_Metadata.filterKeys { it.startsWith(prefix) }. forEach { res[it.key] = m_Metadata[it.key]!!.m_Value }
        return res
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                        USX-to-OSIS translations                        **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * Markers used to handle tag translations requiring special processing.
    */

    enum class TagAction { None, SuppressContent }


    /****************************************************************************/
    /**
    * Enables ConverterUsxToOsis to obtain details of USX-to-OSIS tag
    * translations.
    *
    * @param extendedUsxTagName Name of tag including leading '/' on closing
    *   tags, and including style name on para and char (para:q).
    *
    * @return Corresponding OSIS tag details, or null.
    */

    fun getUsxToOsisTagTranslation (extendedUsxTagName: String): Pair<String, TagAction>?
    {
        return m_UsxToOsisTagTranslationDetails[extendedUsxTagName]
    }


    /****************************************************************************/
    /* This takes the tag list used to reflect a USX start tag, and creates an
    equivalent list to represent the end tag.  I assume here that there is
    no non-tag text before the tag(s), after them, or between them. */

    private fun convertOsisStartToOsisEnd (osisStart: String): String
    {
        var res = ""

        val splitTags = osisStart.split("<")
        for (i in splitTags.size - 1 downTo 1) // 0'th entry will be empty or will contain a non-tag, and can therefore be ignored.
        {
            var s = splitTags[i]
            if (!s.endsWith("/>")) // Don't want to output a closing tag if the original was self-closing.
            {
                s = "/" + s.split(" ")[0]
                if (!s.endsWith(">")) s += ">"
                res += "<$s"
            }
        }

        return res
    }


    /****************************************************************************/
    /* Takes the saved tag translation rows and converts them to usable form. */

    private fun generateTagTranslationDetails ()
    {
       m_RawUsxToOsisTagTranslationLines.forEach { processConverterStepTagTranslation(it) }
    }


    /****************************************************************************/
    /* Converts the saved translation lines to the form needed for further
    processing. */

    private fun processConverterStepTagTranslation (theLine: String)
    {
        /**************************************************************************/
        if (theLine.matches(Regex("(?i).*\\btbd\\b.*"))) return


        /**************************************************************************/
        var line = theLine.substring(theLine.indexOf("=") + 1).trim() // Get the RHS.
        line = expandReferences(line, false)!!.trim()
        val usx = line.split(" ")[0].substring(1).replace("Ͽ", "")



        /**************************************************************************/
        val regex = Regex("(?<key>\\w+)\\s*=\\s*(?<val>Ͼ.*?Ͽ)")
        val attributes: MutableMap<String, String> = HashMap()
        var matchResults = regex.find(line)
        while (null != matchResults)
        {
            val key = matchResults.groups["key"]!!.value
            var value = matchResults.groups["val"]!!.value
            value = value.replace("Ͼ", "").replace("Ͽ", "")
            attributes[key] = value
            matchResults = matchResults.next()
        }



        /**************************************************************************/
        /* If we have an action tag, it involves special processing.  At present
           the only special processing we support is to ditch a tag and all of its
           nested contents. */

        var tagAction: TagAction = TagAction.None
        when (if (attributes.containsKey("action")) attributes["action"] else "")
        {
            "suppressContent" -> tagAction = TagAction.SuppressContent
        }


        /**************************************************************************/
        /* If we have an OSIS tag, it represents both the start and end. */

        val osisEnd: String?
        var osisStart: String? = if (attributes.containsKey("osis")) attributes["osis"] else null
        if (null != osisStart)
        {
            osisEnd = convertOsisStartToOsisEnd(osisStart)
            m_UsxToOsisTagTranslationDetails[usx] = Pair(osisStart, tagAction)
            m_UsxToOsisTagTranslationDetails["/$usx"] = Pair(osisEnd, tagAction)
            return
        }


        /**************************************************************************/
        /* Otherwise, we assume that we have either or both of osisStart and
           osisEnd. */

        osisStart = if (attributes.containsKey("osisStart")) attributes["osisStart"]!! else ""
        osisEnd = if (attributes.containsKey("osisEnd")) attributes["osisEnd"]!! else ""
        m_UsxToOsisTagTranslationDetails[usx] = Pair(osisStart, tagAction)
        m_UsxToOsisTagTranslationDetails["/$usx"] = Pair(osisEnd, tagAction)
    }


    /****************************************************************************/
    /* Saves tag translation lines to a list for later processing.  This enables
       us to apply @(...) expansion.  I can't imagine that we'd ever want to, but
       I want things to be uniform. */

    private fun saveUsxToOsisTagTranslation (line: String)
    {
        m_RawUsxToOsisTagTranslationLines.add(line.split(Regex("="), 2)[1])
    }






    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                            OSIS support                                **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
    * This is fairly revolting.  OSIS requires, in the header, a 'scope' tag
    * giving details of the portions of the Bible covered by the text, in OSIS
    * osisRef format.  Note that in theory, I think we're supposed to be able to
    * get down to the level of individual verses, but I make no attempt to do
    * that here.
    *
    * @return Data for use in scope tag.
    */

    fun UNUSED_makeOsisScope (): String
    {
        /**************************************************************************/
        /* This isn't ideal -- getBookList was set up for other purposes, and
           I'd prefer to have all the output in one variable as book numbers, rather
           than abbreviations.  However, getBookList is what it is. */

        val otBooks: MutableSet<String> = HashSet()
        val ntBooks: MutableSet<String> = HashSet()
        val dcBooks: MutableSet<String> = HashSet()
        val bookNos: MutableSet<Int> = HashSet ()
        getBookList(otBooks, ntBooks, dcBooks)
        otBooks.stream().forEach { bookNos.add(BibleBookNamesUsx.nameToNumber(it)) }
        ntBooks.stream().forEach { bookNos.add(BibleBookNamesUsx.nameToNumber(it)) }
        dcBooks.stream().forEach { bookNos.add(BibleBookNamesUsx.nameToNumber(it)) }

        var low = BibleAnatomy.getBookNumberForStartOfOt()
        var high = BibleAnatomy.getBookNumberForEndOfOt()
        val availableOtBooks = makeOsisScopeChunk(bookNos, low, high)

        low = BibleAnatomy.getBookNumberForStartOfNt()
        high = BibleAnatomy.getBookNumberForEndOfNt()
        val availableNtBooks = makeOsisScopeChunk (bookNos, low, high)

        low = BibleAnatomy.getBookNumberForStartOfDc()
        high = BibleAnatomy.getBookNumberForEndOfDc()
        val availableDcBooks = makeOsisScopeChunk (bookNos, low, high)

        var res = "$availableOtBooks $availableNtBooks $availableDcBooks"
        res = res.trim().replace(Regex("\\s+"), " ")
        return res
    }


    /****************************************************************************/
    private fun makeOsisScopeChunk (bookNos: Set<Int>, low: Int, high: Int): String
    {
        var res = ""
        var ixEnd: Int
        var ixStart = low

        while (true)
        {
            while (ixStart <= high && !bookNos.contains(ixStart)) ++ixStart
            if (ixStart > high) break
            ixEnd = ixStart
            while (ixEnd <= high && bookNos.contains(ixEnd)) ++ixEnd
            --ixEnd
            res += " " + BibleBookNamesUsx.numberToAbbreviatedName(ixStart)
            if (ixStart != ixEnd)
                res += "-" + BibleBookNamesUsx.numberToAbbreviatedName(ixStart)
            ixStart = ixEnd + 1
        }

        res = res.trim().replace(Regex("\\s+"), " ")
        return res
    }


    /****************************************************************************/
    /** This gets rather complicated.  Originally I had hoped to be able to define
    *  this in the same was as all the other config information -- ie in a config
    *  file -- but there are just too many conditional inclusions etc for this
    *  to be really feasible.
    *
    * @return Text for use in short description.
    */

    fun makeStepDescription (): String
    {
        /**********************************************************************/
        /* We want the English and vernacular titles, except where they are
           basically the same thing, in which case the vernacular is essentially
           irrelevant. */

        val englishTitle = get("stepBibleNameEnglish")!!
        var vernacularTitle = get("stepBibleNameVernacular") ?: "\u0001" // Dummy value if not defined.

        val englishTitleLowerCase = englishTitle.lowercase()
        val vernacularTitleLowerCase = vernacularTitle.lowercase()

        if ("eng".equals(get("stepLanguageCode3Char"), ignoreCase = true) ||
            "\u0001" == vernacularTitle ||
            vernacularTitleLowerCase in englishTitleLowerCase ||
            englishTitleLowerCase in vernacularTitle)
          vernacularTitle = "\u0001"



        /**********************************************************************/
        /* And the English and vernacular abbreviations, except, again, where
           they are essentially the same thing. */

        var englishAbbreviation = get("stepAbbreviationEnglish")!!
        var vernacularAbbreviation = get("stepAbbreviationLocal") ?: "\u0001"

        if (englishAbbreviation.lowercase() == vernacularAbbreviation.lowercase() ||
            vernacularAbbreviation == "\u0001")
          vernacularAbbreviation = "\u0001"



        /**********************************************************************/
        /* This hairy piece of code does nothing more interesting than try to
           format the above information in a nice way, where 'nice' depends
           upon which bits of information we have and which we don't. */

        var name = "$englishTitle ($englishAbbreviation) "
        if ("\u0001" != vernacularTitle || "\u0001" != vernacularAbbreviation) name += " \u0002 "
        if ("\u0001" != vernacularTitle) name += "$vernacularTitle "
        if ("\u0001" != vernacularAbbreviation) name += "($vernacularAbbreviation) "
        name = name.replace(") \u0002 (", " \u0002 ")
        name = name.replace("\u0002", "/")




        /**********************************************************************/
        var officialYear = makeStepDescription_getOfficialYear()
        val biblePortion = makeStepDescription_getBiblePortion()
        val language = makeStepDescription_getLanguage(englishTitle)
        val moduleMonthYear = makeStepDescription_getModuleMonthYear().trim()


        var abbreviatedNameOfRightsHolder = get("stepTextOwnerOrganisationAbbreviatedName") ?: ""
        if (abbreviatedNameOfRightsHolder.isNotEmpty()) abbreviatedNameOfRightsHolder += " "

        if (englishTitle.contains(officialYear.trim()) || vernacularTitle.contains(officialYear.trim())) officialYear = "" // DIB: 2021-09-10.

        var text = "$name$abbreviatedNameOfRightsHolder$officialYear $biblePortion$language$moduleMonthYear"
        text = expandReferences(text, false)!!
        text = text.replace("\\s+".toRegex(), " ").trim()
        return text
    }


    /****************************************************************************/
    /* Simplest if I just copy the relevant portion of the spec :-

     - The part of the Bible (OT+NT+Apoc) is only stated if it doesn’t contain
       OT+NT.  This seems to suggest that you don't mention the Apocrypha on
       a text which contains OT+NT even if it's present, and I suspect that's
       not what's meant.

     - If there are five books or fewer, they are listed (eg OT:Ps; Lam +NT).

     - If there are more than give books, but not the complete set, mark the
       text as eg 'OT incomplete +NT'.
    */

    private fun makeStepDescription_getBiblePortion (): String
    {
        /************************************************************************/
        val C_MaxIndividualBooksToReport = 5
        val otBooks: MutableSet<String> = HashSet()
        val ntBooks: MutableSet<String> = HashSet()
        val dcBooks: MutableSet<String> = HashSet()
        getBookList(otBooks, ntBooks, dcBooks)



        /************************************************************************/
        var fullOt = otBooks.size == BibleAnatomy.getNumberOfBooksInOt()
        val fullNt = ntBooks.size == BibleAnatomy.getNumberOfBooksInNt()



        /************************************************************************/
        if (!fullOt && otBooks.isNotEmpty())
        {
            var nOtBooks = otBooks.size
            if (dcBooks.contains("Dag") && !otBooks.contains("Dan")) ++nOtBooks
            if (dcBooks.contains("Esg") && !otBooks.contains("Est")) ++nOtBooks
            fullOt = nOtBooks == BibleAnatomy.getNumberOfBooksInOt()
        }



        /************************************************************************/
        if (fullOt && fullNt) return if (dcBooks.isEmpty()) "" else "+OT2 "
        if (fullOt && ntBooks.isEmpty()) return if (dcBooks.isEmpty()) "OT " else "OT+OT2 "
        if (fullNt && otBooks.isEmpty()) return if (dcBooks.isEmpty()) "NT " else "OT2+NT "



        /************************************************************************/
        if (ntBooks.isEmpty() && otBooks.isEmpty()) return "OT2 only "



        /************************************************************************/
        val otPortion =
          if (fullOt)
              "OT "
          else if (otBooks.isEmpty())
              ""
          else if (otBooks.size > C_MaxIndividualBooksToReport)
               "OT (incomplete) "
          else
          {
              val fullBookList = otBooks.sortedBy { BibleBookNamesUsx.nameToNumber(it) }
              fullBookList.joinToString(", ") + " "
          }



        /************************************************************************/
        val ntPortion =
          if (fullNt)
              "NT "
          else if (ntBooks.isEmpty())
              ""
          else if (ntBooks.size > C_MaxIndividualBooksToReport)
              "NT (incomplete) "
          else
          {
              val fullBookList = ntBooks.sortedBy { BibleBookNamesUsx.nameToNumber(it) }
              fullBookList.joinToString(", ") + " "
          }



        /************************************************************************/
        val apocPortion = if (dcBooks.isEmpty()) "" else " Apocrypha "



        /************************************************************************/
        val haveNtPortion = ntPortion.isNotEmpty()
        val haveApocPortion = apocPortion.isNotEmpty()
        var res = otPortion + if (haveNtPortion || haveApocPortion) " + " else ""
        res += ntPortion + if (haveApocPortion) " + " else ""
        res += apocPortion
        
        if (res.startsWith("+ ")) res = res.substring(2)

        return if (res.isEmpty()) "" else "$res "
    }


    /****************************************************************************/
    /* This is the year which relates to the publication itself, and there are
       various options for it, in descending order of priority :-

        - Most recent copyright year.
        - Publication year.
        - Date-stamp on the datafile.
        - Date the file was downloaded.

       It's difficult to know what to do with this, not least because everything
       seems to be a law unto itself.

       At present I'm dealing with Biblica texts from DBL.  For some of these we
       have been given specific copyright information as part of our agreement
       with DBL.  These do not, in general, identify the copyright year as such
       (ie as an entity in its own right), but they do usually have it within a
       larger string, and -- so long as all of them are like the one I'm looking
       at currently -- will have that preceded by a copyright symbol, so I can at
       least extract the year from the available text.

       Not all of the Biblica texts are like this, however, and where we have not
       been given specific instructions regarding what copyright information to
       use, I am forced back upon the DBL metadata.  Unfortunately, there does not
       seem to be a tag corresponding to the copyright year (not sure if the
       DBL metadata supports one, but even if it does, Biblica aren't using it).

       In the case of the Biblica files, there is freeform text under
       copyright/fullStatement/statementContent, and again it is possible to
       parse this -- although we have to accept that because this is freeform
       text, there is no guarantee that we will always be able to do so.

       If the copyright year cannot be extracted, the STEP spec calls for
       publication year to be used instead.  Assuming this refers to the year
       the translators published the text, rather than the year STEP did its
       stuff, again either DBL lacks a tag to identify this, or else Biblica are
       not using it.  There _is_ a tag 'dateCompleted', but I have no reason to
       believe that is meaningful.  In view of all this, I am not attempting to
       give a publication date.

       The next alternative, according to the STEP specification (above), is the
       date-stamp on the data file.  Again I'm not sure what this means; the only
       sense I can assign to it is the date on which the files were downloaded,
       and that's covered by the final option (below), and therefore does not
       actually constitute a separate option.

       The final alternative is the date the data was downloaded.  However, we
       seldom download stuff much in advance of generating a module for it
       (certainly not often in a different year), and we're giving the module-
       generation date later in the description string which the present method is
       being used to help assemble, and there seems little point in duplicating
       it.

       In summary, therefore, if I can parse and find an actual copyright year in
       the freeform copyright information, then you'll get it (although I'm not
       convinced that we've seen enough different ways of representing this for
       my processing in this respect to be comprehensive).  If I _can't_ get it by
       this means, then I don't bother to give a year at all. */

    private fun makeStepDescription_getOfficialYear (): String
    {
        /************************************************************************/
        val mainPat = Regex("(?i)(&copy;|©|(copyright))\\W*(?<years>\\d{4}(\\W+\\d{4})*)") // &copy; or copyright symbol or the word 'copyright' followed by any number of blanks and four digits.
        val subPat = Regex("(?<year>\\d{4})$") // eg ', 2012' -- ie permits the copyright details to have more than one date, in which case we take the last.
        var res = ""



        /************************************************************************/
        for (key in listOf("stepAboutAsSupplied", "stepShortCopyright"))
        {
            var s = get(key) ?: continue
            var matchResult = mainPat.find(s)
            if (matchResult != null)
              s = matchResult.groups["years"]!!.value

            matchResult = subPat.find(s)
            if (null != matchResult)
            {
                res = matchResult.groups["year"]!!.value
                break
            }
        }



        /************************************************************************/
        return res
    }


    /****************************************************************************/
    /* We want the language name only if it's not English, and is not already
    mentioned in the Bible name. */

    private fun makeStepDescription_getLanguage (bibleNameInEnglish: String): String
    {
        val languageName = get("stepLanguageNameInEnglish")
        val languageNameLowerCase = languageName!!.lowercase()
        if ("english" == languageNameLowerCase) return ""
        return if (bibleNameInEnglish.lowercase().contains(languageNameLowerCase)) "" else " in $languageName "
    }


    /****************************************************************************/
    /* The spec says that the module date should be included only if we make
    changes to the text, for example pursuant to reversification.  However,
    given the potential need to restructure the text so that markup does not
    run across verse boundaries (to keep osis2mod happy), we almost always
    _will_ be making changes, so I always give the date.*/

    private fun makeStepDescription_getModuleMonthYear (): String
    {
        val s = DateTimeFormatter.ofPattern("MMM@yy").format(LocalDate.now())
        return s.replace("@", "'")
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    private fun tidyVal (vv: String): String
    {
        val v = vv.trim()
        if ("{empty}".equals(v, ignoreCase = true)) return ""
        return v.trim().replace(Regex("(?i)\\{space}"), " ")
    }


    /****************************************************************************/
    data class ParameterSetting (var m_Value: String, var m_Force: Boolean)


    /****************************************************************************/
    private val m_BookDescriptors: MutableList<VernacularBookDescriptor> = ArrayList()
    private val m_CopyAsIsLines: MutableList<String> = ArrayList()
    private val m_EnglishDefinitions: MutableSet<String> = mutableSetOf()
    private var m_Initialised: Boolean = false
    private val m_Metadata: MutableMap<String, ParameterSetting> = HashMap()
    private var m_ProcessingEnglishMessageDefinitions = false
    private val m_UsxToOsisTagTranslationDetails: MutableMap<String, Pair<String, TagAction>> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    private val m_RawUsxToOsisTagTranslationLines: MutableList<String> = ArrayList()


    /****************************************************************************/
    private fun getBookListAddBook (otBooks: MutableSet<String>, ntBooks: MutableSet<String>, dcBooks: MutableSet<String>, s: String)
    {
        val bookNo = BibleBookNamesUsx.nameToNumber(s)
        if (BibleAnatomy.isOt(bookNo))
            otBooks.add(s)
        else if (BibleAnatomy.isNt(bookNo))
            ntBooks.add(s)
        else
            dcBooks.add(s)
    }


    /****************************************************************************/
    private fun getBookList (otBooks: MutableSet<String>, ntBooks: MutableSet<String>, dcBooks: MutableSet<String>)
    {
      fun process (filePath: String) { getBookList(otBooks, ntBooks, dcBooks, filePath) }
      BibleBookAndFileMapperEnhancedUsx.iterateOverAllFiles(::process)
    }


    /****************************************************************************/
    private fun getBookList (otBooks: MutableSet<String>, ntBooks: MutableSet<String>, dcBooks: MutableSet<String>, filePath: String)
    {
      /************************************************************************/
      fun processLine (line : String): Boolean
      {
        if (!line.contains("code")) return false

        var s = line.split("code")[1]
        s = s.split(Regex("[\"']"))[1]
        s = s.split(Regex("[\"']"))[0].trim()


        // In theory s is already what we need.  But the conversion below
        // forces the abbreviation into canonical form (and will also check
        // that the abbreviation is one we know about).
        val bookNo = BibleBookNamesUsx.nameToNumber(s)
        s = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
        getBookListAddBook(otBooks, ntBooks, dcBooks, s)

        return true // Finished with this file.
      }



      /************************************************************************/
      try
      {
        File(filePath).forEachLine { if (processLine(it)) throw StepException("")  }
      }
      catch (_: Exception)
      {
      }
  }
}
