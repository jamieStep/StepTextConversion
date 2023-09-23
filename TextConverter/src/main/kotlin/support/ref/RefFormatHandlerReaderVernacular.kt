/****************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesTextAsSupplied
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.LocaleHandler
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.convertRepeatingStringToNumber
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils.replaceRegexOccurrences
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils.splitAndRetainDelimiters
import org.stepbible.textconverter.support.stepexception.StepException
import java.text.NumberFormat
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet



/****************************************************************************/
/**
 * Parses vernacular text to identify embedded references.
 *
 * The class deals with reading and writing individual references, with
 * ranges and with collections.  It does not cater for USX or OSIS -- the
 * former because it has its own processing, and the latter because we have
 * no requirement to read OSIS.
 *
 * I have attempted to make this class as flexible as possible, but with very
 * limited experience of RTL languages, of languages which use non-Roman texts,
 * and with languages which perhaps have a very different view of how references
 * should be represented, it is impossible to know whether the approach I have
 * adopted to configuring all of this will be adequate (or indeed contains
 * things which in practice will turn out to be of not practical use).  And
 * whilst trying to be as flexible as possible, there are, nonetheless some
 * assumptions baked into what I have done, and some things which I have not
 * tested (and probably not even implemented).  I give some details below.
 * (Incidentally, refer to the common configuration file itself for information
 * about configuration -- $common/referenceFormatVernacularDefaults.conf.
 *
 * - I make the assumption that a given element will always be represented in
 *   the same way regardless of context.  For example, I assume that if a
 *   verse is represented as digits in the context of a bcv reference, it will
 *   _always_ be represented as digits in all other flavours of reference.
 *
 * - I have made some provision for RTL texts but since at present I do not have
 *   one to experiment with, nor someone with, say, Arabic knowledge to check
 *   what I have done, the processing remains incomplete and untested.
 *
 *
 *
 *
 * ## Potential issues
 *
 * - I suspect there may be problems with ranges involving single-chapter books.
 *   I haven't managed to wrap my mind around what to do with these.
 *
 * - There are almost certainly issues with singleton elements -- eg numbers
 *   appearing in a text string in isolation, rather than obviously as part of a
 *   reference.  There are two obvious problems with these.  One is that single
 *   elements may well be ambiguous -- if a number appears in a text in isolation
 *   from any other elements which make up a reference, is that number a chapter
 *   or a verse, for instance.  And the other problem is that we may find that
 *   accepting lone numbers as references may be over the top -- the text may
 *   contain a lot of numbers which are just there as ... well, numbers.
 *
 *   My original intention had been to make it configurable, so that you could
 *   say whether you were prepared to accept lone elements or not.  However,
 *   this then makes it difficult to process perfectly valid numbers which
 *   appear as the second part of ranges (Jn 3:16-17), as part of collections,
 *   or (perhaps slightly less likely) near the front of the string, with the
 *   remainder supposedly to be filled in mentally by the reader from context.
 *
 *   In view of this, I do actually accept individual elements as references,
 *   and just kinda hope I'm managing to resolve ambiguities successfully.
 *   This may be a forlorn hope, however.
 *
 * @author ARA "Jamie" Jamieson
 */

object RefFormatHandlerReaderVernacular
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               READING                                  **/
  /**                           Before we start                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* References are appallingly complicated.  Even having taken USX and OSIS
     out of the frame, different vernaculars might in theory differ widely in
     respect of the representations they use.  And even with just one format,
     they may be full references (book, chapter, verse and subverse --
     henceforth b, c, v, s) or partial references (just cv for instance).  The
     missing parts may or may not be filled in from the context (so that 3:16,
     processed in the context of John, may be seen as John 3:16).  The caller
     may have specific requirements which they have to fulfil (the caller may
     require that the book be given explicitly, for instance).  They may form
     part of larger entities (verse ranges or collections).  And they may
     appear in situations where we know a priori that we will have _only_ the
     reference (or range or collection), or where we may have to locate it in
     a larger string containing non-reference text as well -- for example,
     'See also Jn 3:16'.

     All of this suggests a huge reliance upon regular expressions for the
     parsing -- and some pretty complicated processing.  And this is made all
     the more complicated by the need to try to do things in a reasonably
     efficient manner (admittedly less of an issue now than I originally
     anticipated, since I no longer rely upon the processing here to handle
     USX and OSIS).

     To try to simplify the processing (and hopefully also to make it faster)
     I have ignored some of the potential requirements listed above, and also
     made certain simplifying assumptions about what references in general
     may look like.  The latter assumptions I will (probably) discuss later.
     As regards dropping potential requirements, I make no provision for
     callers to stipulate what elements are overtly present in text strings
     which I am parsing: provided I have enough information to create a valid
     reference, I assume that's good enough.  My intention is to _use_ the
     data, not validate it.  Having said which ...




     Valid and invalid references
     ----------------------------

     Internally, a reference is held as an array of four Ints, in the order
     b, c, v, s. In theory any of these can be populated, and any unpopulated.

     Work from the left until you hit the leftmost populated element.  And
     work from the right until you hit the rightmost populated one.  If
     between these two there are any unpopulated elements, I say that the
     reference contains holes.  A reference is valid only if there are no
     holes.  b, bc, cvs, etc are fine.  But bv, cs, etc are not -- the former
     because there is an unpopulated c between b and v, and the latter
     because there is an unpopulated v.

     A reference is 'unconditionally valid' (uvalid) if all of its leading
     entries are populated; but there is no particular requirement for
     _trailing_ entries to be populated too.  Thus b is uvalid, and so is bc
     and bcv and bcvs.  But cv isn't (lacks b).  Etc.

     A reference is 'conditionally valid' (cvalid) if it lacks leading elements,
     but does not contain any holes.  cv is ok, and so is vs, v, etc.

     Normally, our aim is to arrive at uvalid references.  If the textual
     representation contains things which would give us such references,
     that's fine (Jn 3, Jn 3:16, etc).  However, if we are reading vernacular
     text, we may not always have uvalid references -- a vernacular string may
     contain 3:16, for instance, and rely upon the user to understand 'John'
     from the context.

     The processing will itself normally know what context a particular
     reference appears in, and can therefore use the context to fill in
     any empty leading elements in a cvalid reference, thus producing a
     uvalid one.

     On output, incidentally, there are no such complexities: I don't really
     mind what I am asked to do -- if people want incomplete references, so
     be it.



     Summary of processing
     ---------------------

     On, then, to an outline of the processing.  The configuration data gives
     patterns which enable you to recognise b, c, v and s; and also enough
     to recognise any of these (except b) in connection with specific
     disambiguating text, like the 'v' in 'v16'.

     The first section below ses up the information needed to recognise the
     individual elements (b, c, v, s) both when we know a priori that the text
     string we are parsing contains nothing more than a reference and when we
     do not.

     The second section creates the patterns needed to recognise the elements
     in combination, in order to recognise, say, a bcvs reference.

     And the third section is concerned with making use of all this data
     actually to read references, ranges and collections.

     I do make provision here to read individual references, ranges or
     collections directly as such if you know that is what you require.
     However, with vernacular text you are generally unlikely to have that
     luxury -- you won't know in advance whether a given string contains
     references at all, and if it does, whether it contains other noise words
     as well.  Most of the time, therefore, when parsing text you will need
     to invoke readEmbedded, which returns an entire data structure made up
     of some combination of raw text strings and the various flavours of
     reference.
  */





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                    Reading individual references                       **/
  /**            Part 1 -- definitions of individual elements                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* By the end of this section we have ...

     - m_ElementReadersWithinRef: Keyed on b/c/v/s, each entry contains an
       element reader relevant to that kind of data item -- a reader which
       can recognise a book, chapter, verse or subverse when we know the
       string we are parsing will not contain any more than a reference (or
       range or collection).

     - Each reader knows how to recognise the thing for which it is responsible
       when presented with a string containing it, and how to convert the value
       to the integer value used within a Ref structure to represent it.

     - Each reader also potentially sets up functionality to pre-process the
       string being parsed to convert it into a form which will require less
       subsequent processing.  At present only the book handler does this --
       the idea being that recognising books (ie probably at least 66 different
       book names) is going to be expensive, and since we may well have to
       reparse the string several times in order to recognise the things making
       it up, we probably don't want to cope with this multiple times.

     - Not all elements may be present.  If we are processing a text which does
       not have subverses, the user may well not have provided the information
       necessary to recognise them.  And we may well not wish to recognise
       things (subverses in particular) out of the context of verse
       information.

     - The things created here are intended purely for use in part 2 of the
       processing, where we use the individual elements to build things which
       can recognise compound references like Jn 3:16.
  */

  /****************************************************************************/
  private class ElementReader(thePattern: String, converterToInt: (String) -> Int, isKnownToBe: Char?)
  {
    val convertToInt: (String) -> Int = converterToInt
    val pattern: String = thePattern
    val regex: Regex = pattern.toRegex()
  }

  private val m_ElementReaders:       MutableMap<Char, ElementReader> = HashMap() // Used, for instance, when parsing Jn 3:16-17 and we have already read 'Jn 3:16-' and know, therefore, that we are expecting some portion of a reference.
  private val C_FirstCharacterNumericRegex = "\\p{N}".toRegex() // Used to recognise numeric strings (I assume that if the first character is numeric, all of it will be.


  /****************************************************************************/
  /* Makes a reader to handle the task of recognising book names.  We are doing
     the following here:

     - Creating a long regular expression which can recognise all the books of
       interest.  We use this to create a preprocessor which is applied to any
       text to be parsed before the start of the parsing proper.  This
       preprocessor wraps anything it recognises as a book name in ear brackets.
       The idea is that in the parsing proper, we can recognise a book by the
       existence of these ear brackets, rather than having to look for all of the
       book names, something which is expensive.  Since we may have to look for a
       number of patterns all of which contain book names, this should save time.

     - Creating a conversion function which will take a matched string (which
       should reliably be a book name) and convert it to Int representation.

     - Creating a pattern to look for the ear-bracketed book names.  This is the
       pattern which is searched for in the parsing proper.
   */

  private fun makeBookReader () : ElementReader
  {
    /**************************************************************************/
    val toIntFn: (String) -> Int = { text -> BibleBookNamesTextAsSupplied.nameToNumber(text) }



    /**************************************************************************/
    /* Generate a pattern which will match all the books we are looking for.
       To begin with we need a deduplicated list of all the book names of
       interest. */

    val dedup: MutableSet<String> = HashSet()
    val selectedBooks: MutableList<String> = ArrayList()

    fun processName (name: String)
    {
      val lc = name.lowercase()
      if (dedup.contains(lc)) return
      dedup.add(lc)
      selectedBooks.add(name)
    }

    fun processList (names: List<String>)
    {
      names.forEach { processName(it) }
    }

    fun process (length: String)
    {
      when (length)
      {
        "abbr"  -> processList(BibleBookNamesTextAsSupplied.getAbbreviatedNameList())
        "short" -> processList(BibleBookNamesTextAsSupplied.getShortNameList())
        "long"  -> processList(BibleBookNamesTextAsSupplied.getLongNameList())
      }
    }



    /**************************************************************************/
    val accept = m_ConfigParameters["stepReferenceElementInputPattern_b"]!!.lowercase()
    listOf("abbr", "short", "long").filter { accept.contains(it) } .forEach { process(it) }



    /**************************************************************************/
    val patternForPreprocessing = "(?<bA>(?i)\\b(" + selectedBooks.joinToString("|") + ")\\b)" // \b is word boundary.
    val patternForRecognising = "Ͼ(?<b>.*?)Ͽ"



    /**************************************************************************/
    fun preprocessor (x: String): String
    {
      return replaceRegexOccurrences(x, patternForPreprocessing.toRegex()){ y -> "Ͼ" + y + "Ͽ"}
    }


    /**************************************************************************/
    m_PreprocessingFunctions.add(::preprocessor)
    return ElementReader(patternForRecognising, toIntFn, 'b')
  }



  /****************************************************************************/
  /* Makes an appropriately-initialised ElementReader (for c / v / s only --
     books require a different approach). */

  private fun makeElementReader (theType: Char, pre: String = "", post: String = ""): ElementReader?
  {
    /**************************************************************************/
    /* eg the definition for 'v'.  If not present, this element is presumed not
       to require a definition. */

    val defn = m_ConfigParameters["stepReferenceElementInputPattern_$theType"] ?: return null



    /**************************************************************************/
    /* Typical examples look like ...

          Ͼ[0-9]+Ͽ
          Ͼ[0-9]+|(?-i)[A-Z]Ͽ
          Ͼ(?-i)[a-z]+Ͽ

       They are always regular expressions, and always enclosed in ear makers
       which we want to get rid of. */

    var pattern = defn.replace("Ͼ", "").replace("Ͽ","")



    /**************************************************************************/
    /* See if the pattern has a numeric portion (which will always be [0-9]
       in the configuration information, even if the vernacular uses different
       numeric characters).  If it has, note the fact, and then remove the
       start of the numeric range because we'll be looking for open square
       brackets in later processing and don't want to be confused by this
       one. */

    val hasNumeric = defn.contains("[0")
    if (hasNumeric) pattern = pattern.replace("[0", "\u0001") // Get this out of the way for a moment so as not to confuse later processing.



    /**************************************************************************/
    /* Now look for an alpha range, which may look something like [a-z], but
       need not be lowercase, need not be those precise letters, and may perhaps
       not be in Roman characters.  If we find something, alphaFirstChar will
       be set to the character code for the first character in the range, and
       alphaRange to the number of characters covered. */

    var hasAlpha = false
    var alphaFirstChar = '\u0000'
    var alphaLastChar  = '\u0000'
    val alphaRegex = "(?<content>\\[(?<first>.)-(?<last>.)]\\+?)".toRegex() // Parses eg [a-z]+ into its constituent elements.
    val mr = alphaRegex.find(pattern)
    if (null != mr)
    {
      hasAlpha = true
      alphaFirstChar = mr.groups["first"]!!.value.first()
      alphaLastChar = mr.groups["last"]!!.value.first()
    }


    /**************************************************************************/
    /* Undo the temporary change we made above. */

    pattern = pattern.replace("\u0001", "[0") // Reinstate temporary change.



    /**************************************************************************/
    /* If the input contained both alpha and numeric elements, we're going to
       need to enclose the pattern upon which we are working in extra parens
       to hold the alternative values. */

    val additionalOpenParen = if (hasAlpha && hasNumeric) "(" else ""
    val additionalCloseParen = if (hasAlpha && hasNumeric) ")" else ""



    /**************************************************************************/
    /* We now create a pattern with a named group -- the name being the b / c /
       v / s which was passed as argument.  We'll also need the additional
       open paren if there was one.

       The result will be that if a particular piece of text is parsed
       successfully, there will be a group whose name tells us that a b or c
       or v or s has been found.

       Note that I force the pattern to be case-insensitive -- if the user
       wants to enforce case sensitivity, they'll have to add (?-i) to the
       pattern where appropriate. */

    pattern = pattern.replaceFirst("[", "(?<$theType>$additionalOpenParen[")
    pattern = "(?i)$pattern)$additionalCloseParen"



    /**************************************************************************/
    val alphaOffset = if (hasNumeric && hasAlpha) RefBase.C_AlphaChapterOffset else 0
    val isDefinitely = if (pre.isNotEmpty() || post.isNotEmpty()) theType else null
    return ElementReader(pattern, makeReader(alphaFirstChar, alphaLastChar, alphaOffset), isDefinitely)
  }


  /****************************************************************************/
  /* Currying.  This creates a reader which takes just one argument -- a text
     string which has been recognised as a result of regular expression
     processing -- and converts it to the Int used within a ref to represent
     it (the book number, chapter number, etc).

     Where we are handling something inherently numeric, like a verse number,
     this is easy, because we simply take the numeric value.  Where we are
     handling alphabetic values, things are more complicated because we need
     details about the representation in order to perform the conversion.
     (For example, with subverses, we might need to map 'a' to 1, 'aa' to 27,
     etc.)  And for this we need additional information.  Supplying this
     extra information to the conversion method itself would be a bit painful,
     because I want to have a standard signature for the conversion method,
     and I don't want that method to have to take arguments which in some
     cases are meaningless.

     I therefore use this present method to create functions which already
     have any additional information baked into them, and therefore all of
     which need take only the string being parsed.

     alphaOffset will normally be zero.  Where an item matches either a
     numeric string or an alphabetic one, it will be
     RefBase.C_AlphaChapterOffset, so that the values returned for
     alphabetic values do not overlap with those for numeric ones.
   */

  private fun makeReader (firstChar: Char, lastChar: Char, alphaOffset: Int): (String) -> Int
  {
    return { text ->
               if (text[0].toString().matches(C_FirstCharacterNumericRegex))  // If the text starts with a number, I assume it is all numeric.
                 m_NumberFormat.parse(text).toInt()
               else
                 alphaOffset + convertRepeatingStringToNumber(text, firstChar, lastChar)
    }
  }


  /****************************************************************************/
  /* Obtains the information needed to recognise the individual elements which
     make up a reference -- b, c, v and s.  There are a number of things to be
     taken into account here ...

     - The definitions may come in two flavours.  _x flavours (x = b, c, v, s)
       correspond to patterns to look for when looking for the element at a time
       when we know for sure that we are dealing with a reference.  _X flavours
       (B, C, V, S) correspond to patterns to look for when dealing with things
       in a larger string, where it may otherwise not be clear what we are
       dealing with.  Thus v16 in such a string tells us we're dealing with a
       verse where just 16 does not.

     - Each of these gives rise to an ElementReader, but the two flavours are
       stored in different data structures.
   */

  private fun processReaderConfigElementDefinitions ()
  {
    m_ElementReaders['b'] = makeBookReader()
    "cvs".forEach { val reader = makeElementReader(it); if (null != reader) m_ElementReaders[it] = reader }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Reading references                             **/
  /**                  Part 2 -- definitions of references                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* We now have details of how to recognise individual elements.  In this
     section we assemble them into the larger patterns which let us recognise
     eg bcvs references.

     At the end of this, we have a collection of m_ReferenceReader's, each of
     which contains a pattern which enables us to recognise a single flavour
     of reference.  And we have two $$$

     The first has to be a 'full' collection -- bcvs / bcv / bc / cvs / cv / vs
     / c / v / s (I don't accept a standalone b as potentially being a
     reference, so we don't need that).

     I use this when looking for 'complex' references (ie those made up of more
     than one element).  I assume these to be sufficiently distinguishable from
     background noise that if I encounter a match in a string, it _must_ be a
     reference.  Slightly risky, but we'll just have to live with that.

     And I also use them when I already know pretty much for sure that I am
     dealing with a reference and now just need to add to it.  For example, if
     I am looking at ... Jn 3:16-17 ... and have recognised 'Jn 3:16-' and
     therefore know that I'm looking for something to give a second reference
     (which in this case will be the 17, which I need to recognise as a verse
     number).

     The other collection -- keyed on B / C / V / S -- contains patterns which
     would enable me to recognise an element IN ISOLATION in a larger string.
     An example might be recognising the 2 in 'See also v2' as being a verse
     reference.
  */


  /****************************************************************************/
  /* In neither of these do I bother to parse for a book name in isolation.
     I work on the assumption that we would never want to _treat_ this as a
     reference, and that therefore I might as well omit it and improve
     efficiency very slightly. */

  private val m_ReferenceReader: MutableMap<String, Regex> = HashMap() // eg "bcvs" -> a bcvs pattern.
  private val m_ReferenceReaders: MutableList<String> = ArrayList()


  /**************************************************************************/
  /* Creates element readers for bcvs etc.  Called just once before we get
     as far as parsing references. */

  private fun processReaderConfigReferenceDefinitions ()
  {
    listOf("bcvs", "bcv", "bc", "cvs", "cv", "vs", "c", "v", "s").forEach { val regex = makeReferenceRegex(it); if (null != regex) m_ReferenceReader[it] = regex }
    listOf("b", "c", "v", "s").forEach { val regex = makeReferenceRegexSingleton(it);  if (null != regex) m_ReferenceReader[it.uppercase()] = regex }
    makeOrderedListForParsing()
  }


  /***************************************************************************/
  /* This creates a list of parser names ordered according to the required
     precedence -- if adorned singletons come first, then anything containing
     'b', longer first, then according to length, and finally according to
     'prominence' (b before c before v before s). */

  private fun makeOrderedListForParsing ()
  {
    "BCVS".filter { null != m_ReferenceReader[it.toString()] } .forEach { m_ReferenceReaders.add(it.toString()) }

    listOf("bcvs", "bcv", "bc", "cvs", "cv", "vs").filter { null != m_ReferenceReader[it] } .forEach { m_ReferenceReaders.add(it) }

    // If I need to look for subverses, I'll do so explicitly.  Including them in the default list is too risky, because typically almost _anything_ will look like a subverse.
    "bcv".filter { null != m_ReferenceReader[it.toString()] }
         .forEach { m_ReferenceReaders.add(it.toString()) }
  }


  /***************************************************************************/
  /* Uses the configuration data in order to create a pattern which will match
     any singleton-out-of-context patterns.

     As a reminder, these are the things where we encounter something either
     entirely on its own or in the middle of a larger string which might or
     might not be treated as a reference -- for example if we find a '2' in
     some string we are parsing: is this a chapter number, is it a verse
     number, or do we want to ignore it on the basis that it's probably just
     the number of each kind of animal in the Ark?

     These definitions are all based upon configuration parameters like
     V_stepReferenceInputFormatSingletonAdorned_b.

     Returns either a regex to make this particular form of reference, or null
     if there is no definition for this form, or if it relies upon an element
     for which we have no definition (for example if it relies upon a subverse
     in a text which does not have subverses, and for which, therefore, the
     user has not provided a way of recognising subverses).

     The whole pattern is marked as case-insensitive, and starts with a marker
     for a named group <ref>, so that matches can be detected by the presence
     of <ref>.
   */

  private fun makeReferenceRegexSingleton (name: String): Regex?
  {
    /*************************************************************************/
    /* Get the config parameter for this reference.  It may be null if the
       user has opted not to define anything for this particular
       combination. */

    val pattern = m_ConfigParameters["stepReferenceInputFormatSingletonAdorned_$name"] ?: return null
    if (pattern.isEmpty()) return null



    /*************************************************************************/
    val ucName = name.uppercase()
    val regex = "(Ͼ(?<pre$ucName>.*?)Ͽ)?.*?(Ͼ(?<post$ucName>.*?)Ͽ)?".toRegex()
    val mr = regex.matchEntire(pattern)
    var pre  = mr!!.groups["pre$ucName" ]?.value ?: ""
    var post = mr.groups["post$ucName"]?.value ?: ""

    if (pre .isNotEmpty()) pre  = "(?<pre$ucName>$pre)"
    if (post.isNotEmpty()) post = "(?<post$ucName>$post)"

    return makeReferenceRegex(name, pre, post)
  }


  /***************************************************************************/
  /* Uses the configuration data in order to create a pattern which will match
     any of the basic reference patterns bcvs through s.

     Returns either a regex to make this particular form of reference, or null
     if there is no definition for this form, or if it relies upon an element
     for which we have no definition (for example if it relies upon a subverse
     in a text which does not have subverses, and for which, therefore, the
     user has not provided a way of recognising subverses).

     The whole pattern is marked as case-insensitive, and starts with a marker
     for a named group <ref>, so that matches can be detected by the presence
     of <ref>.
   */

  private fun makeReferenceRegex (name: String, pre: String = "", post: String = ""): Regex?
  {
    /*************************************************************************/
    /* Get the config parameter for this reference.  It may be null if the
       user has opted not to define anything for this particular
       combination. */

    var pattern = m_ConfigParameters["stepReferenceInputFormat_$name"] ?: return null
    if (pattern.isEmpty()) return null



    /*************************************************************************/
    /* The pattern will be something like Jn 3:16a -- ie the elements plus
       separators.  Replace Jn by _b_, 3 by _c_ etc, so as to get them into
       a standard form, and then split on these elements. */

    pattern = pattern
      .replace("(?i)Jn".toRegex(), "_b_")
      .replace("3", "_c_")
      .replace("16", "_v_")
      .replace("a", "_s_")

    val splitPattern = splitAndRetainDelimiters(pattern, "(?i)(_b_|_c_|_v_|_s_)").toMutableList()



    /*************************************************************************/
    /* That gives us an array each of whose elements is either _x_ (a pattern
       I chose because it's readable for debugging), or something else, these
       'something else's' representing delimiters.  Delimiters need to be
       surrounded by /Q ... /E in the pattern I generate to mark them as raw
       text.  The other things need to be replaced by the patterns which
       service those elements.

       If we need details of an element which does not exist (most likely
       if we are looking for subverse details and this text does not use
       subverses, so no details have been supplied) then we give up --
       this pattern is not matchable.
    */

    for (i in splitPattern.indices)
      if (splitPattern[i].matches(Regex("_._")))
      {
        val simpleType = splitPattern[i].substring(1, 2)[0] // The subscript turns this into a Char.
        val x = m_ElementReaders[simpleType] ?: return null
        splitPattern[i] = x.pattern
      }
      else
      {
        if (splitPattern[i].isBlank())
          splitPattern[i] = "\\s+"
        else
          splitPattern[i] = "(?i)\\s*\\Q${splitPattern[i]}\\E\\s*"
      }

    val finalPattern = "(?i)$pre(?<ref>" + splitPattern.joinToString("") + ")$post"
    return Regex(finalPattern)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Reading                                   **/
  /**                 Part 3: Definitions for combinations                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private var C_CollectionSeparatorRegex: Regex? = null
  private var C_RangeSeparatorRegex: Regex? = null
  private var C_RangeLowRefComesFirst = true // We may have a definition saying that the low ref comes first (Ref1,Ref2), or last (Ref2,Ref1).


  /****************************************************************************/
  private fun processReaderConfigCombinationDefinitions ()
  {
    var sepPattern = m_ConfigParameters["stepReferenceRangeInputSeparators"]
    if (null != sepPattern)
    {
      C_RangeSeparatorRegex = makePattern(sepPattern).toRegex()
      val x = m_ConfigParameters["stepReferenceCollectionInputFormat"]
      if (null != x) C_RangeLowRefComesFirst = x.indexOf("Ref1") < x.indexOf("Ref2")
    }

    sepPattern = makePattern(m_ConfigParameters["stepReferenceCollectionInputSeparators"]!!)
    C_CollectionSeparatorRegex = sepPattern.toRegex()
  }


  /****************************************************************************/
  private fun makePattern (parm: String): String
  {
    var x = "\u0000" + parm + "\u0000"
    x = x.replace("Ͼ", "\u0001").replace("Ͽ", "\u0001")
    val parts = x.split("\u0001").toMutableList()
    for (i in 1 until parts.size step 2) parts[i] = "(" + parts[i] + ")"
    for (i in 0 until parts.size step 2) parts[i] = "\\Q" + parts[i] + "\\E"
    var res = "(?i)" + parts.joinToString("")
    res = res.replace("\u0000", "")
    res = res.replace("\\Q\\E", "")
    return res
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Reading                                   **/
  /**                     Part 4: The actual read method                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This section is concerned with reading references which may or may not be
     embedded within larger strings -- for example, picking out 'Jn 3:16' from
     the string 'See also Jn 3:16', or just recognising 'Jn 3:16' in a string
     which contains only that.
  */

  /****************************************************************************/
  open class EmbeddedReferenceElement
  {
    fun isAmbiguous (): Boolean { return possibleRefTypes.size > 1 }
    override fun toString (): String { return text }
    var possibleRefTypes: MutableList<String> = mutableListOf()
    var sepType = '-'
    var text: String = ""
  }

  private class EmbeddedReferenceElementDummy: EmbeddedReferenceElement()

  class EmbeddedReferenceElementRefCollection (theRcp: RefCollection, asText: String) : EmbeddedReferenceElement() { val rc = theRcp; init { text = asText } }

  private class EmbeddedReferenceElementRefCollectionPart (theRcp: RefCollectionPart, asText: String) : EmbeddedReferenceElement() { val rcp = theRcp; init { text = asText } }

  private class EmbeddedReferenceElementText (theText: String) : EmbeddedReferenceElement() { init { text = theText } }


  /****************************************************************************/
  /**
  * Attempts to parse a string containing only reference-related information,
  * along, possibly, with noise words.  Returns a list of elements, each of
  * them either text or a reference collection (which may, itself, be made
  * up of one element or many).
  *
  * Note that aside from being horrendously complex, I have made no attempt to
  * make this absolutely watertight -- there may well be awkward situations it
  * does not cater for.
  *
  * @param text Text to be parsed.
  *
  * @param context A reference giving the context within which we are parsing.
  *   So, for example, if you are parsing a string saying '3:16' and you pass
  *   a context here which has Jn as the book, the '3:16' will be interpreted as
  *   Jn 3:16.  May be null, in which case there is no defaulting.
  *
  * @param preferenceForFirstRef Used to resolve ambiguities in the first or
  *   only element.  For example, if you are parsing a string consisting of just
  *   a number (which may often represent either a chapter number or a verse
  *   number), and if 'c' has been passed as the preference, then the number
  *   is interpreted as a chapter.  May be null, in which case an ambiguous
  *   first reference is regarded as an error.  May be a full-stop separated
  *   list, in which case we run left to right until we find one which works.
  *   Note that this applies *only* to the first ref.  Other refs are always
  *   defaulted from a context determined by the most recent ref within the
  *   string being parsed.
  *
  * @return Collection representing parsed text.
  */

  fun readEmbedded (text: String, context: Ref? = null, preferenceForFirstRef: String? = null): List<EmbeddedReferenceElement>
  {
    /***************************************************************************/
    //Dbg.dCont(text, "Mt 24: 45, 47")


    /***************************************************************************/
    /* Preprocessing to get the string into a form where regular expression
       handling may be more efficient.  (Basically I'm putting ear characters
       around book names so I haven't got to keep on running through the entire
       list of books each time I look for a match.  Instead I can just look for
       a string within ear characters.) */

    var myText = text; m_PreprocessingFunctions.forEach { myText = it.invoke(myText) }
    var myContext = context



    /***************************************************************************/
    /* Start by splitting on range and collection separators. */

    val elts: MutableList<EmbeddedReferenceElement> = mutableListOf()
    elts.addAll(splitAndRetainDelimiters(myText, "(" + C_RangeSeparatorRegex!!.pattern + ")|(" + C_CollectionSeparatorRegex!!.pattern + ")").map { EmbeddedReferenceElementText(it) })



    /***************************************************************************/
    /* Flag all separators. */

    elts.filter { it.text.matches(C_RangeSeparatorRegex!!) } .forEach { it.sepType = 'R' }
    elts.filter { it.text.matches(C_CollectionSeparatorRegex!!) } .forEach { it.sepType = 'C' }



    /***************************************************************************/
    /* Run over each chunk checking to see if it could be interpreted as a
       reference.  m_ReferenceReaders is ordered such that non-ambiguous matches
       come first.  As a result, elt.possibleRefTypes will have the most likely
       match at the front; but it will contain alternatives where there are
       others which also work.
     */

    var foundReferences = false
    fun tryMatchForReference (elt: EmbeddedReferenceElement)
    {
      m_ReferenceReaders.forEach {
        if (m_ReferenceReader[it]!!.matches(elt.text))
        {
          elt.possibleRefTypes.add(it)
          foundReferences = true
        }
      }
    }

    elts.filter { '-' == it.sepType }. forEach { tryMatchForReference(it) } // Runs over everything except separators.



    /***************************************************************************/
    /* If there's nothing which can be interpreted as a reference, just return
       the original string. */

    if (!foundReferences)
      return listOf(EmbeddedReferenceElementText(text))



    /***************************************************************************/
    /* If the caller has specified how to resolve ambiguities for the first
       reference, and if the first reference is indeed ambiguous, see if we can
       resolve the ambiguity in the way suggested by the caller.

       preferenceForFirstRef may be null (no resolution is supplied), or a '.'
       separated list of options (v, c, vs, etc), in which case they are
       presumed to be in order of precedence and we take the first which works.
    */

    val ixFirstRef = elts.indexOfFirst { it.possibleRefTypes.isNotEmpty() }
    if (elts[ixFirstRef].isAmbiguous())
    {
      if (null == preferenceForFirstRef)
        throw StepException("First reference in $text is ambiguous and no disambiguating parameter has been supplied.")

      val options = preferenceForFirstRef.split(".")
      run findRefType@ {
        options.forEach {
          if (it in elts[ixFirstRef].possibleRefTypes)
          {
            elts[ixFirstRef].possibleRefTypes = mutableListOf(it)
            return@findRefType
          }
        }
      }

      if (elts[ixFirstRef].isAmbiguous()) // Failed to resolve matters.
        throw StepException("First reference in $text is ambiguous and no disambiguating parameter $preferenceForFirstRef doesn't help resolve things.")
    }



    /***************************************************************************/
    fun peckingOrder (selector: Char): Int
    {
      return "bcvs".indexOf(selector.lowercase())
    }



    /***************************************************************************/
    /* Cater for all ambiguities.  It's not _quite_ appropriate to assume that
       each reference must fit in the context of any previous one, I don't
       think, but it will serve.  It's difficult to work out what's needed
       here, but I _think_ the following will serve:

       - If the (n+1)'th is non-ambiguous, then obviously we have to retain it
         as-is.  My assumption, built-in elsewhere, is that anything
         containing a book will automatically be non-ambiguous, so I don't need
         to include any special processing for this.

       - If it can be adjusted to _start_ with something which the n'th _ends_
         with (eg the n'th is bcv, and the (n+1)'th starts out as c but can be
         turned into v), then do that.  This will work only if initially it
         has been identified as coming too high up in the hierarchy.
    */

    var prevRef = elts[ixFirstRef]
    elts.subList(ixFirstRef + 1, elts.size)
        .filter { it.isAmbiguous() }
        .forEach {
          if (peckingOrder(it.possibleRefTypes[0].first()) < peckingOrder(prevRef.possibleRefTypes[0].last()))
          {
            val ix = it.possibleRefTypes.indexOfFirst { it.first().lowercase() == prevRef.possibleRefTypes[0].last().lowercase() }
            if (-1 == ix)
              throw StepException("!!!")
            else
            {
              it.possibleRefTypes = mutableListOf(it.possibleRefTypes[ix])
            }
          }

          prevRef = it
        }



    /***************************************************************************/
    /* Turn everything marked as a reference into an actual reference element,
       based upon the actual text content along with the rolling context.
       Where we have ambiguities, this will set the element according to the
       preferred ambiguity.  We may need to override this later because of the
       context (for example in a range where the first element includes cv, and
       therefore a lone number as the second element needs to be interpreted as
       a verse).  We'll deal with that in due course. */

    elts.forEachIndexed { ix, elt ->
      if (elt.possibleRefTypes.isNotEmpty()) // This identifies things we think are going to be references.
      {
        val preferredRefType = elt.possibleRefTypes[0]
        val ref = readRefRaw(elt.text, preferredRefType, myContext)
        elts[ix] = EmbeddedReferenceElementRefCollectionPart(ref, elt.text)
        elts[ix].possibleRefTypes = mutableListOf(preferredRefType)
        myContext = ref
      }
    }



    /***************************************************************************/
    /* Look for and process ranges.  I work on the basis that if I recognise
       something as being a range, it's ok to regard it as such -- ie that I
       will never decide to reject that conclusion later. */

    var ix = 0
    while (ix + 2 < elts.size)
    {
      /************************************************************************/
      /* To be a range, we must have a ref followed by a range separator
         followed by a ref, and the two refs must be appropriate to _be_ a
         range (same book etc). */

      if (elts[ix    ] !is EmbeddedReferenceElementRefCollectionPart ||
          elts[ix + 2] !is EmbeddedReferenceElementRefCollectionPart ||
          'R' != elts[ix + 1].sepType)
      {
        ++ix
        continue
      }



      /************************************************************************/
      /* The high ref of a range must never be specified in excess of the low
         range -- eg we can't have cv followed by bcv.  Also I don't cater for
         a book in the high ref. */

      val eltLow  = elts[ix]     as EmbeddedReferenceElementRefCollectionPart
      val eltHigh = elts[ix + 2] as EmbeddedReferenceElementRefCollectionPart
      if (peckingOrder(eltHigh.possibleRefTypes[0].first()) < peckingOrder(eltLow.possibleRefTypes[0].first()))
        throw StepException("Bad vernacular reference range (high end is overspecified): $text.")
      if (eltHigh.possibleRefTypes[0].contains("b", ignoreCase = true))
        throw StepException("Bad vernacular reference range (high end contains book): $text.")



      /************************************************************************/
      /* OK to collapse the refs to a range. */

      val refLow  = eltLow.rcp.getLowAsRef()
      val refHigh = eltHigh.rcp.getLowAsRef()
      elts[ix] = EmbeddedReferenceElementRefCollectionPart(RefRange(refLow, refHigh), eltLow.text + elts[ix + 1].text + eltHigh.text)
      elts[ix + 1] = EmbeddedReferenceElementDummy()
      elts[ix + 2] = EmbeddedReferenceElementDummy()
      ix += 3
    } // while ix (processing ranges)



    /***************************************************************************/
    /* Look for and process collections. */

    ix = 0
    while (ix < elts.size)
    {
      /************************************************************************/
      /* To be a collection, we must have a CollectionPart followed by a
         collection separator, followed by a CollectionPart.  Unlike with
         ranges, we can have a number of consecutive bits which make up the
         collection.  At the end of this loop, either ixSub will not have
         moved (in which case we do not have a collection), or else it will
         point at the last CollectionPartTemp to be included in the
         collection. */

      var ixSub = ix
      while (ixSub + 2 < elts.size &&
            elts[ixSub    ] is EmbeddedReferenceElementRefCollectionPart &&
            elts[ixSub + 2] is EmbeddedReferenceElementRefCollectionPart &&
            'C' == elts[ix + 1].sepType)
        ixSub += 2 // Move to the ending CollectionPartTemp



      /************************************************************************/
      /* Not making a multi-element collection.  Of course this may be a single
         RefCollectionPartTemp, and ultimately we'll want to turn these into
         collections too, but that can come later. */

      if (ixSub == ix)
      {
        ++ix
        continue
      }


      /************************************************************************/
      val rc = RefCollection()
      var s = ""
      for (i in ix .. ixSub step 2) rc.add((elts[i] as EmbeddedReferenceElementRefCollectionPart).rcp)
      for (i in ix .. ixSub) s += elts[i].text
      for (i in ix .. ixSub) elts[i] = EmbeddedReferenceElementDummy()
      elts[ix] = EmbeddedReferenceElementRefCollection(rc, s)
      ix = ixSub + 1
    }



    /***************************************************************************/
    /* Turn singleton RefCollectionPart's into RefCollections. */

    elts.forEachIndexed { ix, elt ->
      if (elt is EmbeddedReferenceElementRefCollectionPart)
      {
        val rc = RefCollection()
        rc.add(elt.rcp)
        elts[ix] = EmbeddedReferenceElementRefCollection(rc, elt.text)
      }
    }



    /***************************************************************************/
    return elts.filterNot { it is EmbeddedReferenceElementDummy }
  }


  /****************************************************************************/
  /* This reads a reference in a situation where we are expecting the entire
     string to hold something recognisable as a reference. It does not worry
     about ambiguities -- it simply returns a ref, and assumes that the
     text will indeed furnish one. */

  private fun readRefRaw (text: String, selector: String, context: Ref?): Ref
  {
    /**************************************************************************/
    fun getGroup (mr: MatchResult, groupName: String): String?
    {
      return try
      {
        mr.groups[groupName]!!.value
      }
      catch (_: Exception)
      {
        null
      }
    }



    /**************************************************************************/
    val regex = m_ReferenceReader[selector]!!
    val mr = regex.find(text)!!



    /**************************************************************************/
    val b = getGroup(mr,"b")
    val c = getGroup(mr,"c")
    val v = getGroup(mr,"v")
    val s = getGroup(mr,"s")



    /**************************************************************************/
    val bx = if (null == b || null == m_ElementReaders['b']) RefBase.C_DummyElement else m_ElementReaders['b']!!.convertToInt.invoke(b)
    val cx = if (null == c || null == m_ElementReaders['c']) RefBase.C_DummyElement else m_ElementReaders['c']!!.convertToInt.invoke(c)
    val vx = if (null == v || null == m_ElementReaders['v']) RefBase.C_DummyElement else m_ElementReaders['v']!!.convertToInt.invoke(v)
    val sx = if (null == s || null == m_ElementReaders['s']) RefBase.C_DummyElement else m_ElementReaders['s']!!.convertToInt.invoke(s)



    /**************************************************************************/
    val res = Ref.rd(bx, cx, vx, sx)
    if (null != b) res.setSourceB(RefBase.ElementSource.Explicit)
    if (null != c) res.setSourceC(RefBase.ElementSource.Explicit)
    if (null != v) res.setSourceV(RefBase.ElementSource.Explicit)
    if (null != s) res.setSourceS(RefBase.ElementSource.Explicit)



    /**************************************************************************/
    Ref.canonicaliseRefsForSingleChapterBooks(res)
    res.mergeFromOther(context)
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Initialisation                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

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
  private fun processReaderConfig ()
  {
    processReaderConfigElementDefinitions()
    processReaderConfigReferenceDefinitions()
    processReaderConfigCombinationDefinitions()
  }


  /****************************************************************************/
  private val m_ConfigParameters: MutableMap<String, String?> = HashMap() // A local copy of the relevant configuration information taken from ConfigData.
  private var m_NumberFormat: NumberFormat
  private val m_PreprocessingFunctions: MutableList<(String) -> String> = ArrayList() // Used at the beginning of parsing to convert the string to a form more amenable to later processing.



  /****************************************************************************/
  init
  {
    readConfig()
    m_NumberFormat = LocaleHandler.getVernacularNumberFormat()
    processReaderConfig()
  }
}
