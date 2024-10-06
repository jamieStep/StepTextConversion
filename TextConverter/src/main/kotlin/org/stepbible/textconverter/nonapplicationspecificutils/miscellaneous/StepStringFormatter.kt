/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.util.*



/******************************************************************************/
/**
 * Creates formatted strings.
 *
 * This builds on String.formatter to permit arguments to be specified by name,
 * and to support output of Bible references.
 *
 *
 *
 *
 *
 * ## Placeholders and arguments
 *
 * As a reminder, the format of an argument placeholder within a format string
 * in the standard String.format is :-
 *
 *     %[argument_index$][flags][width][.precision]conversion
 *
 *     eg         %3$      -      10    .5           f    (%3$-10.5f)
 *       for a left-justified floating point conversion in a field of width
 *       10 with 5 digits after the decimal point, picking up argument number
 *       3 from the list.
 *
 * The first extension is that 'argument_index' may now be an argument *name*,
 * as an alternative to an ordinal position.  Placeholders must be identified
 * consistently, however -- if you have multiple placeholders in a format string,
 * then all must carry names, or all must carry numbers overtly, or none may
 * carry either of these (in which case it is as though the placeholders had
 * been numbered in increasing order from 1).
 *
 * The argument list to [format] may be given in a number of forms.  In the
 * plain vanilla form of String.formatter, I think the only option is a
 * varargs.  The options now are:
 *
 * - A map, relating field names to values (in which case the placeholders in
 *   the format string must identify arguments by name).
 *
 * - A map, relating field numbers (in String or Int form) to values (in which
 *   case the placeholders in the format string must identify arguments by
 *   number -- explicitly by actually giving each an overt number, or implicitly
 *   by giving none a number, and relying instead upon the order in which they
 *   appear).  Explicit field numbers are 1-based, because that's the way
 *   Java's String.formatter does things.
 *
 * - Any iterable collection of items -- typically a list or an array.  In
 *   this case, the placeholders in the format string may identify arguments
 *   by number, or need not identify them at all, with the implication that
 *   the list already has the arguments in the correct order.
 *
 * - If the argument list has more than one entry, or if the one entry it
 *   has is not a map or an Iterable, it is treated as varargs in the way
 *   that String.formatter normally expects.
 *
 * - The arguments may include Ref, RefRange or RefCollection instances
 *   (which, of course, assumes that there are corresponding placeholders
 *   in the format string: this is another extension, and is discussed in
 *   more detail below).  Other than this, all of the usual things are
 *   supported (numbers, strings, dates, etc).
 *   
 * - If you use the map forms, and there are Ref, RefRange or RefCollection
 *   instances to be handled, you can include a special entry with a key of
 *   @contextReference which is a Ref giving the context in which the
 *   reference will be seen, thus making defaulting possible.  (If you have
 *   *several* Refs / RefRanges / RefCollections in the list, each supplies
 *   the context for the next.)
 *
 *
 * There are also additional options as regards the format string (aside
 * from the fact, already discussed, that it can identify the values it
 * requires by name):
 * 
 * - It can contain portions of the form @(thing).  These are replaced with
 *   the value obtained by looking up 'thing' in the configuration data.
 *   
 * - It can contain placeholders for Refs / RefRanges / RefCollections.
 *   These placeholders are discussed in the next section (or they would
 *   be if I didn't simply point you at somewhere else ...)
 *
 *
 *
 *
 * 
 * ## Handling of references
 * 
 * A placeholder for a reference looks like :-
 *
 *     %[argument_index$]Ref<format>
 *
 * 'Ref' may be replaced by RefO, RefU or RefV if you wish to force the
 * format to be OSIS, USX or vernacular.  If you have more than one
 * reference placeholder to be processed, there is no need to keep repeating
 * RefU or whatever -- you can simply use Ref, and the previous value will
 * be assumed.  (If you use Ref on the first such placeholder, vernacular is
 * assumed, since in most cases you will be using formatted strings to
 * produce text which will be read by the user.)
 *
 * The format string is discussed in more detail in [RefFormatHandlerReaderVernacular].  In
 * essence, though, it should simply be a substring of "bcvs", in that order.
 * If you give bcvs, then all references will be spelt out in full -- book,
 * chapter, verse (and subverse if the reference has one).  If you give cv, then
 * all references will consist of just a chapter and verse -- etc.
 * 
 * The actual format associated with bcvs etc is configurable.  Refer to
 * {@link RefFormatHandler}, and to the comments in referenceFormatUsx.conf.
 *
 * You can also append "-" to the format string to invoke defaulting.  In this
 * case, each reference is compared with its predecessor, and only those
 * elements which differ are output.  The very first element is spelt out in
 * full according to the formatting string you supply -- except that you can
 * supply here a context reference, in which case the first element to be output
 * will be defaulted relative to that.
 *
 * There is one further option -- you can give the format string as "e" (short
 * for 'explicit' -- and indeed you can give the value "explicit" if you wish).
 * In this case, there is no defaulting.  Each reference is output according to
 * the elements which were explicitly defined within it.  By way of further
 * explanation, when a reference is created (most particularly when it is
 * created by parsing a string), we may be explicitly given all of the elements
 * which make it up; but in some cases, we may receive only <i>some</i> of the
 * elements, the remaining ones being defaulted from context.  The "e" option
 * causes the output to be tailored to display only the elements which were
 * explicitly supplied.
 *
 * @author ARA "Jamie" Jamieson
 */

object StepStringFormatter: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun convertNameAndValueListToMap (vararg otherArgs: Any): Map<String, Any>
  {
    val res: MutableMap<String, Any> = HashMap()
    for (i in otherArgs.indices step 2) res[otherArgs[i] as String] = otherArgs[i + 1]
    return res
  }


  /****************************************************************************/
  /**
   * Creates a formatted string, but supports an awful lot of extensions en
   * route to that point.  There is more information in the head-of-module
   * comments.
   *
   * This method is the entry point for the module, and it also smooths out the
   * differences between the various forms of input.  More details are available
   * in the head-of-module comments.
   *
   * @param theFmt Format string.
   * @param otherArgs  Things filled in to format string.
   * @return Formatted string.
   */

  fun format (theFmt: String, vararg otherArgs: Any): String
  {
    /**************************************************************************/
    //Dbg.d(theFmt, "[%RefV<a-a>]")



    /**************************************************************************/
    /* First, the really easy bit.  I permit @(...) within format strings, which
       is filled in from the Config parameter named within the parens. */

    val fmt = expandAtReferencesInFormat(theFmt) // Replace @(...) references with data from ConfigData.



    /**************************************************************************/
    /* If there are no arguments to place into ths string, that's all we need
       do. */

    if (otherArgs.isEmpty())
      return fmt



    /**************************************************************************/
    /* I now want to get the argument list into standard form.  I make various
       assumptions here :-

       - If the first entry in the argument list it itself a map, it relates
         field names to values, and is already partway towards being what we
         require.

       - If the first entry is an Iterable, I convert it to a map which relates
         "1" to the first entry, "2" to the second, and so on.

       - Anything else, I treat as an ordinary varargs list as Java
         String.formatter would expect.  For the sake of uniformity, this, too,
         I convert to the form described in the previous bullet point. */

    val args: Map<String, Any> = if (otherArgs[0] is Map<*, *>)
      otherArgs[0] as Map<String, Any> // Already a map.
    else if (otherArgs[0] is Iterable<*>)
      (otherArgs[0] as Iterable<*>).mapIndexed { index, value -> (index + 1).toString() to value!! } .toMap() // First arg is a list.
    else
      (otherArgs as Array<*>).mapIndexed { index, value -> (index + 1).toString() to value!! } .toMap() // More than one arg.



    /**********************************************************************/
    /* If otherArgs is based on field _names_, it may contain a default
       reference, which isn't actually part of the data (it merely forms
       the default for the first reference which is to be output).  We
       need to identify it separately. */

    val dflt: Ref? = args["@contextRef"] as Ref?



    /**************************************************************************/
    /* Get the format string into canonical form and order the arguments
       appropriately.  Then replace any Ref placeholders with strings, and the
       corresponding arguments with the appropriate string representation. */

    val (canonicalFmtA, orderedArgListA) = standardiseData(fmt, args)
    val (canonicalFmtB, orderedArgListB) = handleRefs(canonicalFmtA, orderedArgListA, dflt)



    /**************************************************************************/
    return String.format(canonicalFmtB, *(orderedArgListB.toTypedArray()))
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Expands @(...) references. */

  private fun expandAtReferencesInFormat (theFmt: String): String
  {
    /**************************************************************************/
    /* %% is used to indicate a literal %.  Get these out of the way temporarily
       to avoid confusion. */

    var fmt = theFmt.replace("%%", "\u0001")



    /**************************************************************************/
    /* Things like @(thing) are replaced by looking up 'thing' in the
       configuration data. */

    fun process (mr: MatchResult): String
    {
      return ConfigData[mr.groups["name"]!!.value] ?: throw StepExceptionWithStackTraceAbandonRun("StepStringFormatter couldn't find config attribute " + mr.groups["name"]!!.value)
    }



    /**************************************************************************/
    fmt = C_Regex_MetadataSubstitution.replace(fmt) { process(it) }

    
    
    /**************************************************************************/
    /* Replace the temporary markers. */

    fmt = fmt.replace("\u0001", "%%")
    return fmt
  }


/****************************************************************************/
  /* Looks for places where we are expecting to have refs.  Converts the
     refs to formatted form, and modifies the format string to accept a
     string value rather than a Ref. */

  private fun handleRefs (formatString: String, otherArgs: List<Any>, originalContext: Ref?): Pair<String, List<Any>>
  {
    /**************************************************************************/
    //Dbg.dCont(formatString, "[%1\$RefV<a-a>]")



    /**************************************************************************/
    /* %% is used to indicate a literal %.  Get these out of the way temporarily
       to avoid confusion. */

    var fmt = formatString.replace("%%", "\u0001")
    val revisedArgs = otherArgs.toMutableList()



    /**************************************************************************/
    /* We now run over all Ref-type arguments creating formatted versions of
       them, which we fill into the appropriate elements in the argument
       array. */

    var context: Ref? = if (null == originalContext) null else Ref.rd(originalContext)
    var lastTypeUsed = "U" // USX vs OSIS etc.



    /**************************************************************************/
    fun process (mr: MatchResult): String
    {
      /************************************************************************/
      /* We would expect a valid reference here.  However, a major user of this
         class is reversification, and there are quite a few places where that
         has, ostensibly as references, things which cannot be parsed as such,
         because they are required to give additional information (like a
         reference and a number of alternative references).  These things will
         turn up here as strings rather than references, so we need to leave
         them as such and then alter the formatting string to accommodate
         them. */

      val ixAsString = mr.groups["index"]!!.value
      val ix = ixAsString.toInt()
      if (revisedArgs[ix - 1] is String)
        return "%$ixAsString\$s"



      /************************************************************************/
      /* We have a reference.  We need to convert it to the appropriate string
         form and then update the argument list and formatting string
         accordingly. */

      val rc =
        if (otherArgs[ix - 1] is RefCollection)
          otherArgs[ix - 1] as RefCollection
        else if (otherArgs[ix - 1] is RefRange)
          RefCollection(otherArgs[ix - 1] as RefRange)
        else
          RefCollection(otherArgs[ix - 1] as Ref)

      var type = mr.groups["type"]!!.value
      if (type.isEmpty()) type = lastTypeUsed
      lastTypeUsed = type
      val formatHandler = when (type.lowercase())
      {
        "o" -> RefFormatHandlerWriterOsis
        "u" -> RefFormatHandlerWriterUsx
        "v" -> RefFormatHandlerWriterVernacular
        else -> RefFormatHandlerWriterVernacular
      }

      val thisFormat = mr.groups["format"]!!.value
      revisedArgs[ix - 1] = formatHandler.toString(rc, thisFormat, context)
      context = rc.getLastAsRef()
      return "%$ixAsString\$s"
    }

    fmt = fmt.replace(C_Regex_Ref) { process(it) }



    /**************************************************************************/
    /* Replace the temporary markers. */

    fmt = fmt.replace("\u0001", "%%")
    return Pair(fmt, revisedArgs)
  }


  /****************************************************************************/
  /* The format string may contain %...$ to identify a placeholder (with '...'
     being either a number or a name); or it may simply contain '%' to mark a
     placeholder.

     The job of this method is to replace all %...$ by suitable 1-based
     original values, and to return an argument list ordered correspondingly. */

  private fun standardiseData (formatString: String, rawArgs: Map<String, Any>): Pair<String, List<Any>>
  {
    /**************************************************************************/
    val orderedArgs: MutableList<Any> = ArrayList()
    var fmt = formatString
    var hadIdentifiers = false



    /**************************************************************************/
    /* %% is used to indicate a literal %.  Get these out of the way temporarily
       to avoid confusion. */

    fmt = fmt.replace("%%", "\u0001")



    /**************************************************************************/
    /* This is called for each match of %...$.  It extracts the '...' portion
       as 'id', and uses this to identify the argument which should be added
       to the list of arguments.  If the match involved a numbered argument,
       then it returns the original match, except that the trailing % is
       replaced by \u0002 to avoid later processing (we convert it back
       later).  If the match involved a named argument, the id is replaced
       by the ordinal number of the corresponding value in the list we are
       building up.*/

    fun processIdentifier (mr: MatchResult): String
    {
      hadIdentifiers = true
      val identifier = mr.groups["id"]!!.value
      orderedArgs.add(rawArgs[identifier]!!)
      val x = if ("0123456789".contains(identifier[0]))
        mr.value
      else
        mr.value.replace(identifier, orderedArgs.size.toString())
      return x.replace("%", "\u0002")
    }

    fmt = C_Regex_IdentifiedEntries.replace(fmt) { processIdentifier(it) }



    /**************************************************************************/
    var n = 0
    fmt = fmt.replace("%".toRegex()) { "%${++n}\$" }
    if (n > 0)
    {
      if (hadIdentifiers) throw StepExceptionWithStackTraceAbandonRun("Mixed forms of identifier in format string: $formatString")
      rawArgs.keys.map { it.toInt() }.sorted().forEach { orderedArgs.add(rawArgs[it.toString()]!!)}
    }



    /**************************************************************************/
    /* Replace the temporary markers. */

     fmt = fmt.replace("\u0001", "%%")
     fmt = fmt.replace("\u0002", "%")



    /**************************************************************************/
    return Pair(fmt, orderedArgs)
  }


  /****************************************************************************/
  private val C_Regex_IdentifiedEntries = "%(?<id>.+?)\\$".toRegex()
  private val C_Regex_Ref = "(?i)%(?<index>\\d+?)\\\$(?<refPortion>Ref(?<type>[OSUV])?<(?<format>.*?)>)".toRegex()
  private val C_Regex_MetadataSubstitution = "\\$\\((?<name>.*?)\\)".toRegex() // eg @(myMetadataKey)
}