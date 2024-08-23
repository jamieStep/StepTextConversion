/******************************************************************************/
package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.stepexception.StepException


/******************************************************************************/
/**
 * Evaluates the rule from a single reversification row.
 *
 * @author A Jamieson www.stepbible.org
 */

open class ReversificationRuleEvaluator (dataCollection: X_DataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
   * This class evaluates reversification rules -- the conditions which
   * determine whether or not a given row in the reversification data applies
   * to the Bible being processed.
   *
   * There are two parts to rule evaluation -- a particular verse must exist
   * (the source ref from the reversification data), and the rule itself must
   * pass.
   *
   * A rule may consist of zero or more conditions.  If it contains zero
   * conditions, the test for applicability always passes.  Otherwise, all of
   * the individual conditions must evaluate to true for the rule to pass (ie
   * combinations of rules always combine via 'and').
   *
   * The types of conditions are as follows :-
   *
   *     - xxx=Exist: Checks if the given reference exists in the Bible.
   *       xxx may be either a verse or a subverse (but never subverse
   *       zero). If xxx is a verse and only subverses exist, the test passes.
   *       If xxx is a subverse and only the verse exists, the test fails.
   *
   *     - xxx=NotExist: Inverse of above test.
   *
   *     - xxx=Last: Checks whether a given verse is the last in its owning
   *       chapter.  xxx is only ever a verse.  If the owning chapter does not
   *       exist, the test fails.  If only subverses exist, the test still
   *       passes.
   *
   *     - xxx < yyy or xxx > yyy: Checks if the number of *words* in
   *       reference xxx is less than (greater than) the number of words in yyy.
   *       So far as I can see these tests only ever involve verses, never
   *       subverses.  If either reference exists only as subverses, the test is
   *       applied to the aggregate length of all the subverses.  If either
   *       reference does not exist (neither as a verse nor as subverses), the
   *       test fails.
   *
   *     - With the length tests just described, either side may have appended
   *       to it '\*n' -- eg Gen.1:1 \* 2 > Gen.2:3 -- in which case the number
   *       of words is multiplied by that number.  n may be integer or real.
   *       You may also have '+' to add the number of words together -- eg
   *       Gen.1:1 \* 2 + Gen.1:2 \* 3.  And latterly, instead of the '+',
   *       you may simply have a verse range, which is the same as having
   *       all of the individual verses separated by '+'.
   *
   * Note that where canonical titles are involved, you can have eg
   * Psa.13:Title where a verse reference would otherwise be required.
   */


  /****************************************************************************/
  /**
   * Checks to see if a given rule passes.
   *
   * @param sourceRef The reference for the source verse.
   * @param theRuleData The rule.
   * @param row Raw row data, used only in error messages.
   * @return True if the rule passes.
   */

  fun rulePasses (sourceRef: Ref?, theRuleData: String, row: ReversificationDataRow): Boolean
  {
    /**************************************************************************/
    //Dbg.d(row.rowNumber in listOf(1123))
    //Dbg.dCont(theRuleData, "Num.26:28>Num.26:29")



    /**************************************************************************/
    if (null != sourceRef)
    {
      if (0 == sourceRef.getV()) // Within the reversification data, the canonical title is held as v0, but we need to split that case out for existence checks.
      {
        if (!m_BibleStructure.hasCanonicalTitle(sourceRef)) return false
      }
      else
      {
        if (!m_BibleStructure.verseOrSubverseExistsAsSpecified(sourceRef)) return false
      }
    }



    /**************************************************************************/
    m_Row = row
    val ruleData = theRuleData.replace("\\s+".toRegex(), "").lowercase()
    if (ruleData.isEmpty()) return true

    if (m_KnownResults.containsKey(ruleData)) return m_KnownResults[ruleData]!! // Save repeated calculations.

    val res = rulePasses(ruleData)
    m_KnownResults[ruleData] = res
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
  /* Carries out verse-length comparisons.

     I make allowance for various options ...

     - We may have a direct comparison -- eg Gen.1:1 > Gen.1:2 -- in which case
       we simply compare the number of words.

     - We may have a factor -- 2 * Gen.1:1 > Gen.1:2 -- in which case we compare
       n times the length of one verse against the other.  I don't mind whether
       we have 2 * Gen.1:1 or Gen.1:1 * 2; and you can have the factor on either
       or both sides of the equality test.

     - We can have several verses -- Gen.1:1 > Gen.1:2 + Gen.1:3 + Gen.1:4 -- in
       which case the lengths of the various verses are added together.  The
       sum can appear on either or both of the two sides.

     - We can have a collection of verses rather than a single one.  I _think_ I
       allow for any kind of collection here, although the only one presently
       used is a range, I think -- Gen.1:1 > Gen.1:2-4.  You can also have a
       factor as above -- eg Gen.1:1 > 2 * Gen.1:2-4.

     - Tests may involve verses or subverses.  Where you ask for a verse which
       actually exists as subverses, the number of words is the sum of the
       numbers for each of the subverses.
  */

  private fun lengthComparison (text: String): Boolean
  {
    /**************************************************************************/
    //Dbg.d(text, "1ki.6:1<1ki.6:6*2")



    /**************************************************************************/
    val sides = text.split("([<>])".toRegex())
    val totalLengths = mutableListOf(0, 0)



    /**************************************************************************/
    for (side in sides.indices)
    {
      val elts = sides[side].split("+").toMutableList() // Bits to be added together.
      for (i in elts.indices)
      {
        val isTitle = elts[i].contains("title")
        if (isTitle) elts[i] = elts[i].replace(":title", "")

        var fac: Double?
        var refCollectionAsString: String
        val bits = elts[i].split("*") // Attempt to split out factors.
        if (1 == bits.size) // No factor.
        {
          refCollectionAsString = bits[0]
          fac = 1.0
        }
        else
        {
          fac = bits[0].toDoubleOrNull() // We have a factor, but it may come before or after the verse.
          if (null == fac)
          {
            fac = bits[1].toDouble()
            refCollectionAsString = bits[0]
          }
          else
            refCollectionAsString = bits[1]
        }

        val refs = RefCollection.rdUsx(ReversificationData.usxifyFromStepFormat(refCollectionAsString)).getAllAsRefs()

        var overallSize = 0
        var ref = m_BackstopDefaultRef

        refs.forEach {
          ref = Ref.rd(it, ref)
          val thisSize = if (isTitle) m_BibleStructure.getCanonicalTextSizeForCanonicalTitle(ref.toRefKey_bc())!! else m_BibleStructure.getCanonicalTextSize(ref.toRefKey_bcvs())

          when (thisSize)
          {
            BibleStructure.C_ElementInElision   ->
            {
              lengthWarning("$ref forms part of an elision, and we cannot therefore carry out length tests upon it.")
              return false
            }

            else -> overallSize += (fac * thisSize).toInt()
          }
        } // refs.forEach

        totalLengths[side] += overallSize
      } // elts
    } // sides



    /**************************************************************************/
    val res = totalLengths[0] < totalLengths[1]
    return if (text.contains("<")) res else !res
  }


  /****************************************************************************/
  private fun lengthWarning (theMsg: String)
  {
    val msg = "$theMsg ($m_Row)"
    IssueAndInformationRecorder.reversificationIssue(msg)
  }


  /****************************************************************************/
  /* As far as determining whether the rule passes or not, there is no need for
     the two separate loops below.  However, some rules involve length tests,
     and occasionally length tests may be meaningless because in a particular
     text they are being applied to elided verses, and where this is the case I
     issue a warning message.  The use of the two loops makes it possible to
     avoid length tests on the first pass, in the hope that I can determine the
     result of the evaluation without applying the length test, and will
     therefore not issue the warning message. */

  private fun rulePasses (ruleText: String): Boolean
  {
    val rules = ruleText.split("&")
    var res = rules.filter { "<" !in it   &&   ">" !in it } .firstOrNull { !subrulePasses(it) }; if (null != res) return false
        res = rules.filter { "<"  in it   ||   ">"  in it } .firstOrNull { !subrulePasses(it) }; return null == res
  }


  /****************************************************************************/
  private fun subrulePasses (theText: String): Boolean
  {
    /**************************************************************************/
    if (theText.isEmpty() || "always" == theText)
      return true



    /**************************************************************************/
    var text = theText
    if (text.contains("last"))
    {
      var ref = m_BackstopDefaultRef
      text = text.replace("last", "").replace("=", "").trim()
      ref = RefCollection.rdUsx(ReversificationData.usxifyFromStepFormat(text), ref, "v").getFirstAsRef()
      return m_BibleStructure.getLastVerseNo(ref.toRefKey_bc()) == ref.getV()
    }



    /**************************************************************************/
    /* Exist / NotExist is the only case where we may be being asked about a
       canonical header (':title').  In this case, we need to check the
       existence of the header, rather than the existence of the verse. */

    if (text.contains("exist"))
    {
      val invert = text.contains("not")
      val res: Boolean
      text = text.replace("exists", "").replace("exist", "").replace("not", "").replace("=", "").trim()

      var ref = m_BackstopDefaultRef

      if (text.contains(":title"))
      {
        text = text.split(":")[0]
        ref = RefCollection.rdUsx(ReversificationData.usxifyFromStepFormat(text), ref, "v").getFirstAsRef()
        res = m_BibleStructure.hasCanonicalTitle(ref)
      }
      else
      {
        ref = RefCollection.rdUsx(ReversificationData.usxifyFromStepFormat(text), ref).getFirstAsRef()
        res = m_BibleStructure.verseOrSubverseExistsAsSpecified(ref.toRefKey_bcvs())
      }

      return if (invert) !res else res
    }



    /**************************************************************************/
    if (text.contains("<") || text.contains(">"))
      return lengthComparison(text)



    /**************************************************************************/
    throw StepException("Unknown reversification rule: $text")
  }


  /****************************************************************************/
  private val m_BibleStructure = dataCollection.getBibleStructure()
  private val m_BackstopDefaultRef = Ref.rd(999, 999, 999, 0)
  private val m_DataCollection = dataCollection
  private lateinit var m_Row: ReversificationDataRow
  private val m_KnownResults: MutableMap<String, Boolean> = HashMap()


  /****************************************************************************/
  init {
    //Dbg.d(m_DataCollection.getDocument())
    if (!m_BibleStructure.hasCanonicalTextSize())
      m_DataCollection.reloadBibleStructureFromRootNodes(wantCanonicalTextSize = true)
  }
}

