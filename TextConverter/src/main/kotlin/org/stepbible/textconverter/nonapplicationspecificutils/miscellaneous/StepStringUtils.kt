package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import java.util.*

/******************************************************************************/
/**
 * Miscellaneous string-related utilities.
 *
 * @author ARA "Jamie" Jamieson
 */

object StepStringUtils
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Takes a string comprising one or more whitespace-separated words, trims
   * it, and returns a string where the first letter of each word is capitalsed,
   * with words separated by a single space.
   *
   * @param s String to be processed.
   * @return Capitalised string.
   */

  fun capitaliseWords (s: String): String
  {
    val x = s.split("\\s+".toRegex()).toMutableList()
    return x.joinToString(" "){ it[0].uppercase() + it.substring(1) }
  }


  /****************************************************************************/
  /**
  * Takes a potentially multi-line string.  Replaces newlines by spaces,
  * multiple spaces by single spaces, and trims spaces from start and end.
  *
  * @param text The text to be processed.
  * @return Amended text.
  */

  fun forceToSingleLine (text: String?): String?
  {
    return text?.replace("\n", " ")?.replace("\\s+".toRegex(), " ")?.trim()
  }


  /****************************************************************************/
  /**
   * Converts a string to a boolean value.  If the string starts with Y (for
   * Yes) or T (for True), then the returned value is true.  Anything else
   * (including an empty string) returns false.  Checks are not case-sensitive.
   *
   * @param theText Text to be examined.
   *
   * @return True if the string suggest a Yes or a True.
   */

  fun getYesNo (theText: String): Boolean
  {
    var text = theText
    text = text.trim { it <= ' ' }.lowercase()
    return text.startsWith("y") || text.startsWith("t")
  }


  /****************************************************************************/
  /**
  * Checks if all characters in a given string are ASCII.  (Strictly, we're
  * really more interested in whether they're all letters, numbers or a few
  * punctuation characters, because this is normally used when looking at
  * module names, but ASCII is probably good enough.
  *
  * @param s String to be examined.
  * @return True if all characters are ASCII.
  */

  fun isAsciiCharacters (s: String): Boolean = null == s.firstOrNull { it.code > 127}


  /****************************************************************************/
  /**
   * Marks balanced parens.  (  (   )  ) becomes (.001.  (.002.   .002.)  .001.)
   *
   * @param text Text to be marked up.
   *
   * @return Modified string.
   */

  fun markBalancedParens (text: String): String
  {
    val C_Pat = "(?<char>[()])".toRegex()
    var level = 0

    val res = C_Pat.replace(text) {
      val bracket = it.groups["char"]!!.value
      if ("(" == bracket)
        "(." + String.format("%03d", ++level) + "."
      else
        "." + String.format("%03d", level--) + ".)"
    }

    return res
  }


  /****************************************************************************/
  /**
  * Undoes the effect of markBalancedParentheses, qv
  *
  * @param text Text to be processed.
  * @return Modified text.
  */

  fun unmarkBalancedParens (text: String): String
  {
    return text.replace("\\(\\.\\d\\d\\d\\.".toRegex(), "(").replace("\\.\\d\\d\\d\\.\\)".toRegex(), ")")
  }


  /****************************************************************************/
  /**
  * Surrounds a string with quote marks.
  *
  * @param s: String to be handled.
  * @param quote: Quote mark.
  * @return Quoted string.
  */

  fun quotify (s: String, quote: String = "\"") = quote + s + quote


  /****************************************************************************/
  /**
  * Replaces all matches for a given regular expression by something based upon
  * the matched value.  A sample call might look like:
  *
  *    replaceRegexOccurrences("a...bb...cccc...", "(a+|b+|c+)".toRegex()) { "/" + it + "/" })
  *
  * which would modify the string so that all runs of a's, b's and c's were
  * enclosed in slashes.
  *
  * I have a feeling there is probably a better way to do this -- either one
  * is computationally less expensive, or which involves less complicated
  * coding, but at present this is the best I can come up with.  At least having
  * it here within one method means there is only one place to look if I do come
  * up with something better.
  *
  * @param text String to be processed.
  * @param regex Regular expression.
  * @param mapper Takes the matched value and converts it to something else.
  * @result Modified string.
  */

  fun replaceRegexOccurrences (text: String, regex: Regex, mapper: (String) -> String): String
  {
    var s = text
    regex.findAll(s).asIterable().reversed().forEach { s = s.substring(0, it.range.first) + mapper(it.value) + s.substring(it.range.last + 1) }
    return s
  }


  /****************************************************************************/
  /** */
  /**
   * Converts a string to safe sentence case -- ie all lower case except the
   * first character, which is upper case, and all non-word characters removed.
   *
   * @param text Input string.
   * @return Modified string.
   */

  fun safeSentenceCase (text: String): String
  {
    var s = text
    s = s.replace("\\W+".toRegex(), "")
    return s.substring(0, 1).uppercase() + s.substring(1).lowercase(Locale.getDefault())
  }


  /****************************************************************************/
  /**
   * Splits a string, and at the same time retains the delimiters.  Not easy
   * to do this 'properly'.  The commented-out code does it, but in order to
   * do so, it creates a regular expression which contains two copies of the
   * sep pattern, and if the pattern contains named groups, this is a problem,
   * because Kotlin rejects a regular expression which has the same name more
   * than once.  The alternative code gets round this, but only at the expense
   * of using \u0001 and \u0002 internally, and therefore won't work if the
   * input string itself contains either of these two characters.
   *
   * @param s String to be split.
   * @param sep Separator at which to split.
   * @return Split string.
   */

  fun splitAndRetainDelimiters (s: String, sep: String): List<String>
  {
    //val res = s.split("((?=^)|(?<=^))".replace("^", sep).toRegex())
    //return res.subList(1, res.size - 1) // The previous statement always seems to return an empty string at start and end.

    //Dbg.d(">>>" + s + "<<<")
    val text = sep.toRegex().replace("\u0001$s\u0001") { "\u0002${it.value}\u0002" }
    val bits = text.split("\u0002").toMutableList()
    bits[0] = bits[0].replace("\u0001", "")
    bits[bits.size - 1] = bits[bits.size - 1].replace("\u0001", "")
    val ixStart = if (bits[0].isEmpty()) 1 else 0
    val ixEnd = if (bits.last().isEmpty()) bits.size - 2 else bits.size - 1
    return bits.subList(ixStart, ixEnd + 1)
  }


  /****************************************************************************/
  /**
   * Uppercases the first character of a string, and downcases everything
   * else.
   *
   * @param s String to be handled.
   *
   * @return Modified string.
   */

  fun sentenceCaseFirstLetter(s: String): String
  {
    return if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1).lowercase()
  }
}




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                      String receiver functions                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns a count of the number of characters in a string, excluding
   * punctuation and all flavours of space.
   *
   * @return Count of characters.
   */

  fun String.characterCount () = this.replace("\n", "").replace("\\W+".toRegex(), "").length


  /****************************************************************************/
  /**
   * Returns a count of the number of words in a string.
   *
   * @return Count of words.
   */

  fun String.wordCount (): Int
  {
    val s = this.replace("\n", " ").replace("\\W+".toRegex(), " ").trim { it <= ' ' }.replace("\\s+".toRegex(), " ")
    return if (s.isBlank()) 0 else s.split("\\p{Z}+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
  }
