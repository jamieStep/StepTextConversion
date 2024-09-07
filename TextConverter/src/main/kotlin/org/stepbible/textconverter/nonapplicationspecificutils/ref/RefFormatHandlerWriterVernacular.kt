package org.stepbible.textconverter.nonapplicationspecificutils.ref

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesTextAsSupplied
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.LocaleHandler
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.shared.BookNameLength
import java.text.NumberFormat

/****************************************************************************/
/**
 * A writer for vernacular references.
 *
 * CAUTION: You may well find that this has been little exercised, and so may
 * not work.  I *would* use this functionality mainly in the context of
 * cross-references, and there I go to some lengths to use vernacular text
 * supplied in the raw USX files, and therefore do not employ the
 * functionality here.
 *
 * @author ARA "Jamie" Jamieson
 */

object RefFormatHandlerWriterVernacular: RefFormatHandlerWriterBase()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Writing references                             **/
  /**            Part 1 -- definitions of individual elements                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_ElementWriters: MutableMap<Char, (Int) -> String> = HashMap()


  /****************************************************************************/
  private fun processWriterConfigElementDefinitions ()
  {
    "cvs".forEach { m_ElementWriters[it] = makeElementWriter(it) }

    val bookLength = m_ConfigParameters["stepReferenceElementOutputDefault_b"] ?: "abbreviated"

    when (bookLength[0].lowercase()[0])
    {
      'a' -> m_ElementWriters['?'] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Abbreviated) }
      's' -> m_ElementWriters['?'] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Short)       }
      'l' -> m_ElementWriters['?'] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Long)        }
    }

    m_ElementWriters['-'] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Abbreviated) }
    m_ElementWriters['='] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Short)       }
    m_ElementWriters['+'] = { x -> BibleBookNamesTextAsSupplied.numberToName(x, BookNameLength.Long)        }
  }


  /****************************************************************************/
  private fun makeElementWriter (type: Char): (Int) -> String
  {
    /**************************************************************************/
    /* If we have no definition, assume we accept just numbers. */

    var parm = m_ConfigParameters["stepReferenceElementInputPattern_$type"] ?: return { x -> m_NumberFormat.format(x) }



    /**************************************************************************/
    /* If the thing includes numbers -- [0, 9] -- make a note of the fact and
       then do enough to hide it so it won't interfere with our attempts to
       see if it includes alpha ranges. */

    val wantNumeric = parm.contains("[0")
    parm = parm.replace("[0", "")
    val alphaMr = Regex("\\[(?<range>.*?)]").find(parm)



    /**************************************************************************/
    /* If we're only dealing with numeric, return a numeric handler. */

    if (wantNumeric && null == alphaMr) return { x -> m_NumberFormat.format(x) }



    /**************************************************************************/
    /* We're definitely dealing with alpha, so get details of the start of the
       range and the number of elements. */

    val range = alphaMr!!.groups["range"]!!.value
    val low = range[0]
    val high = range.last()



    /**************************************************************************/
    /* Give back either a pure alpha handler or one which will accept both
       numeric and alpha. */

    if (!wantNumeric)
      return { x -> MiscellaneousUtils.convertNumberToRepeatingString(x, low, high) }
    else
      return { x -> if (x < RefBase.C_AlphaChapterOffset) m_NumberFormat.format(x) else MiscellaneousUtils.convertNumberToRepeatingString(x, low, high) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Writing references                             **/
  /**                Part 2 -- definitions of references                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The aim here is simply to associate each different combination -- bcvs,
     bcv, c, v, etc -- with a string which is based upon the format specified
     in the configuration file (which, as a reminder, is some combination of
     the elements 'Jn', '3', '16' and 'a').

     Here I replace Jn, 3, 16 and a respectively by \u0001, \u0002, \u0003 and
     \u0004.  This makes it easier to substitute stuff into these strings when
     producing formatted output, because I can be reasonably confident that
     nothing I introduce to the string is going to conflict with these special
     characters.
  */

  /****************************************************************************/
  private val m_ReferenceWriters: MutableMap<String, String> = HashMap()


  /****************************************************************************/
  private fun processWriterConfigReferenceDefinitions ()
  {
    fun process (key: String)
    {
      val fmt = m_ConfigParameters[key]!!
      val category = key.split("_")[1]
      m_ReferenceWriters[category] = fmt.replace("Jn", "\u0001").replace("3", "\u0002").replace("16", "\u0003").replace("a", "\u0004")
    }

    m_ConfigParameters.keys.filter { it.startsWith("stepReferenceOutputFormat") }.forEach { process(it) }
    m_ReferenceWriters["a"] = ""
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Writing references                             **/
  /**               Part 3 -- definitions of combinations                    **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This section is concerned with looking to see if there are any special
     formatting requirements for elements in the context of ranges or
     collections.
   */

  /****************************************************************************/
  private var m_CollectionDefaultFormat = ""
  private var m_RangeDefaultFormat  = ""


  /****************************************************************************/
  private fun processWriterConfigCombinationDefinitions ()
  {
    var bits = m_ConfigParameters["stepReferenceRangeOutputFormat"]!!.split("/")
    var x = bits[0].split("_")
    m_RangeDefaultFormat = if (1 == x.size) "bcvs" else x[1]
    x = bits[1].split("_")
    m_RangeDefaultFormat += "/" + if (1 == x.size) "bcvs" else x[1]

    bits = m_ConfigParameters["stepReferenceOutputCollectionFormat"]!!.split("/")
    x = bits[0].split("_")
    m_CollectionDefaultFormat = if (1 == x.size) "bcvs" else x[1]
    x = bits[1].split("_")
    m_RangeDefaultFormat += "/" + if (1 == x.size) "bcvs" else x[1]
  }





  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Writing references                           **/
  /**                            Part 4: Doing it                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Here we are given a reference or a range or a collection, and need to
     write it out.  What we write out depends not only upon the item itself,
     but also upon the format and context arguments, if supplied.


     Individual references
     ---------------------

     The format string may be some combination of bcvs, or it may be 'a'.  If
     null, bcvs is assumed.  b may be replaced by one of b-, b= or b+.  And
     any one of the characters may be in upper case ...

     The easy option is 'a' (for 'as-is').  In this case, we simply write out
     the supplied reference based upon elements which were overtly supplied at
     the time it was read (as opposed to elements which may have been
     defaulted).  Thus if 3:16 were read, 3:16 would be written out, even if
     we had been able to deduce that the reference actually referred to John
     and had therefore filled in the book based on that knowledge.

     Moving on, if something appears in upper case, it _must_ be included in
     the output.  So if you have a format string like Bcvs, you will definitely
     get the book name in the output.  If the reference you are trying to
     process lacks this element, processing will be aborted.

     If an element appears in lower case, you're happy to have it output, but
     equally can live without if appropriate (this has to do with handling the
     context, which is covered shortly).  So if you have, say, bCv, you are
     saying that you are happy to have the book and verse if appropriate, that
     you _must_ have the chapter, and that you don't want the subverse.

     Incidentally, b-, b= and c+ respectively force the output to give the
     abbreviated, sort or long form of the book name if available.  (If no
     details of the requested type of name were provided, the processing
     will do its best with what's available.)  'b' on its own means to use
     whatever default was specified in the configuration data.  The default
     default, as it were, is abbreviated.  (And you can of course have all
     of these in upper case too -- B, B-, B=, B+.)

     Moving on to the use of any context value.  If a context variable is
     supplied, then leading elements which are the same between the supplied
     reference and the context reference are candidates for omission -- and
     will be omitted unless a capital letter in the format string forces the
     issue.  So, if the reference to be output is Jn 3:16 and the context
     value is Jn 3, and you use a format string of bcvs, the output will be
     just '16', because b and c are the same.  But if you give bCvs, the
     output will be 3:16, because you are forcing the chapter to be displayed
     (and by implication everything after it).  Note, incidentally, that
     there is no harm in including in the format string something -- 's'
     in this case -- which does not appear in the reference to be handled:
     it will simply be ignored.



     Compound references
     -------------------

     We turn now to ranges and collections.

     In full form, format strings here consist of two parts, comma-separated.
     The first indicates how the first element of the range or collection is to
     be handled; and the second indicates how all remaining elements are to be
     handled.  The second is optional.  If omitted it is taken as bcvs.  And the
     format string itself is optional, in which case it is treated as though
     both parts were bcvs.

     The first reference is handled in the way described above, taking the first
     part of the format string and any supplied defaults.  The second and
     subsequent elements are handled using the second part of the format string,
     and taking the previous element as the context (in a collection, if the
     previous element was a range, we use the top of the range as the default
     for the next item in the collection).
   */



  /****************************************************************************/
  /**
  * Returns the separator used between collection elements when the elements
  * are in different chapters.
  *
  * @return Separator.
  */

  fun getCollectionSeparatorDifferentChapters (): String
  {
    return m_ConfigParameters["stepReferenceOutputCollectionSeparatorDifferentChapters"]!!
  }


  /****************************************************************************/
  /**
  * Returns the separator used between collection elements when the elements
  * are in the same chapter.
  *
  * @return Separator.
  */

  fun getCollectionSeparatorSameChapter (): String
  {
    return m_ConfigParameters["stepReferenceOutputCollectionSeparatorSameChapter"]!!
  }


  /****************************************************************************/
  /**
  * Converts a single reference to string form.
  *
  * @param ref Reference to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (ref: Ref, format: String?, context: Ref?): String
  {
    val formats = makeFormatString(format, ref).toMutableList()

    val eIx = formats.indexOf("e")
    if (-1 != eIx) formats[eIx] = ref.getExplicitElementSelectors()

    val (_, fmtSelector, bookLengthSelector) = makeWriterSelector(ref, formats[0], context)
    var res = m_ReferenceWriters[fmtSelector]!!
    if ("\u0001" in res) res = res.replaceFirst("\u0001", m_ElementWriters[bookLengthSelector]!!(ref.getB()))
    if ("\u0002" in res) res = res.replaceFirst("\u0002", m_ElementWriters['c']!!(ref.getC()))
    if ("\u0003" in res) res = res.replaceFirst("\u0003", m_ElementWriters['v']!!(ref.getV()))
    if ("\u0004" in res) res = res.replaceFirst("\u0004", m_ElementWriters['s']!!(ref.getS()))
    return res
  }


  /****************************************************************************/
  /**
  * Converts a reference range to string form.
  *
  * @param refRange Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (refRange: RefRange, format: String?, context: Ref?): String
  {
    val formats = makeFormatString(format, refRange.getLowAsRef())
    val part1 = toString(refRange.getLowAsRef(), formats[0], context)
    val part2 = toString(refRange.getHighAsRef(), formats[1], refRange.getLowAsRef())
    val sep = if (Ref.sameChapter(refRange.getLowAsRef(), refRange.getHighAsRef())) m_ConfigParameters["stepReferenceOutputRangeSeparatorSameChapter"]!! else m_ConfigParameters["stepReferenceOutputRangeSeparatorDifferentChapters"]!!
    return part1 + sep + part2
  }


  /****************************************************************************/
  /**
  * Converts a single collection to string form.
  *
  * @param refCollection Item to be handled.
  * @param format Optional format string.
  * @param context Optional Ref giving context information.
  */

  override fun toString (refCollection: RefCollection, format: String?, context: Ref?): String
  {
    val formats = makeFormatString(format, refCollection.getLowAsRef())

    val elements = refCollection.getElements()
    var res: String = toString(elements[0], formats[0], context)

    val sepSameChapter = m_ConfigParameters["stepReferenceOutputCollectionSeparatorSameChapter"]!!
    val sepDifferentChapters = m_ConfigParameters["stepReferenceOutputCollectionSeparatorDifferentChapters"]!!

    var prevElement = elements[0]
    elements.subList(1, elements.size).forEach { res += (if (sameChapter(it, prevElement)) sepSameChapter else sepDifferentChapters) + toString(it, formats[1], prevElement.getHighAsRef()); prevElement = it }

    return res
  }


  /****************************************************************************/
  /* The caller may supply a format string, or may give it as null.  If
     supplied, it may consist of either a single element or a pair, dash-
     separated.  If it is null, we take it as being bcvs-bcvs.  If it
     contains only a single element, we take it as that element followed by
     bcvs.  The first part of the resulting string is used to format the first
     element of the thing being output, and the second for everything else.

     There is one further wrinkle: if the format string contains 'a', it is
     taken as implying that the output should contain whatever elements
     were explicitly fed into the input (as opposed to any which were defaulted
     from context).
  */

  private fun makeFormatString (requested: String?, ref: Ref): List<String>
  {
    var x = if (requested.isNullOrEmpty()) "bcvs" else if (requested.contains("a")) requested.replace("a", ref.getExplicitElementSelectors()) else requested
    var myFormat = x ?: "bcvs"
    if (!myFormat.contains("-")) myFormat += "-bcvs"
    return myFormat.split("-")
  }


  /****************************************************************************/
  /* Based upon the Ref we are trying to output, the format and the context,
     returns details of which format to use, what book length and if necessary
     a revised Ref. */

  private data class WriterSelectorDetails (val ref: Ref, val fmtSelector: String, val bookLengthSelector: Char)
  private fun makeWriterSelector (ref: Ref, format: String?, context: Ref?): WriterSelectorDetails
  {
    /**************************************************************************/
    val formatRequested = format ?: "bcvs"



    /**************************************************************************/
    val revisedRef = if (null == context) ref else { val newRef = Ref.rd(ref); newRef.mergeFromOther(context); newRef }
    var bookNameLengthSelector = '?'
    var fmtSelector = ""



    /**************************************************************************/
    fun process (elt: Char)
    {
      if ("-=+".contains(elt))
      {
        bookNameLengthSelector = elt
        return
      }

      if (elt.isUpperCase())
        fmtSelector += elt.lowercase()
      else if (revisedRef.hasExplicit(elt))
        fmtSelector += elt
    }

    formatRequested.forEach { process(it) }



    /**************************************************************************/
    return WriterSelectorDetails(revisedRef, fmtSelector, bookNameLengthSelector)
  }


  /****************************************************************************/
  /* Checks if two elements of a reference collection belong to the same
     chapter. */

  private fun sameChapter (a: RefCollectionPart, b: RefCollectionPart): Boolean
  {
    return Ref.sameChapter(a.getHighAsRef(), b.getLowAsRef())
  }


  /****************************************************************************/
  private val m_ConfigParameters: MutableMap<String, String?> = HashMap() // A local copy of the relevant configuration information taken from ConfigData.
  private var m_NumberFormat: NumberFormat





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Writing references                             **/
  /**                           Initialisation                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  init
  {
    readConfig()
    m_NumberFormat = LocaleHandler.getVernacularNumberFormat()
    processWriterConfig()
  }


  /****************************************************************************/
  /* Reads the relevant config parameters.  Also handles ditto marks used on
     output parameters. */

  private fun readConfig ()
  {
     val configParameters = ConfigData.getValuesHavingPrefix("V_")
     configParameters.keys.forEach { m_ConfigParameters[it.replace("V_", "")] = configParameters[it] }
     m_ConfigParameters.keys.forEach { if ("\"" == m_ConfigParameters[it]) m_ConfigParameters[it] = m_ConfigParameters[it.replace("Output", "Input")]}
  }


  /****************************************************************************/
  private fun processWriterConfig ()
  {
     processWriterConfigElementDefinitions()
     processWriterConfigReferenceDefinitions()
     processWriterConfigCombinationDefinitions()
  }
}
