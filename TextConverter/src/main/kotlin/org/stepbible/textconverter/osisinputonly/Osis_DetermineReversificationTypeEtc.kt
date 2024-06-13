/******************************************************************************/
package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.processingelements.PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation
import org.stepbible.textconverter.utils.BibleStructure
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg


/******************************************************************************/
/**
* Determines reversification and osis2mod types etc.
*
* @author ARA "Jamie" Jamieson
*/

object Osis_DetermineReversificationTypeEtc
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (bibleStructureUnderConstruction: BibleStructure) = doIt(bibleStructureUnderConstruction)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Hmm ... this gets rather complicated, as will shortly be apparent. */

  private fun doIt (bibleStructureUnderConstruction: BibleStructure)
  {
    /**************************************************************************/
    Dbg.reportProgress("Determining reversification requirements etc.")



    /**************************************************************************/
    /* The parameters which control what we're going to do. */

    m_NrsvScheme = "NRSV" + if (bibleStructureUnderConstruction.hasAnyBooksDc()) "A" else "" // _If_ this text fitted NRSV(A), would we want NRSV or NRSVA?
    m_FitsWithNrsv = fitsWithNrsv(bibleStructureUnderConstruction)                           // _Does_ it actually fit with NRSV?
    m_IsCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText")                   // What it says on the tin.
    getCanonicalisedVersificationSchemeIfAny()                                               // Check if we've been given a scheme, and if so, convert the name to canonical form.



    /**************************************************************************/
    /* At this juncture we could split the processing up in any number of
       different ways, depending upon the control parameters just obtained.
       However, the issue of whether or not it is a copyright text is probably
       the most important, because it has external (legal) implications. */

    if (m_IsCopyrightText)
      doCopyrightText()
    else
      doNonCopyrightText()



   /**************************************************************************/
   /* Report what we're doing. */

   val okToGenerateFootnotes = ConfigData.getAsBoolean("stepOkToGenerateFootnotes")
   val osis2modType = ConfigData["stepOsis2modType"]!!
   val reversificationType = ConfigData["stepReversificationType"]
   val stepSoftwareVersionRequired = ConfigData["stepSoftwareVersionRequired"]
   val targetAudience = ConfigData["stepTargetAudience"]!!
   val versificationScheme = ConfigData["stepVersificationScheme"]
   Logger.info("Treating this as a ${if (m_IsCopyrightText) "copyright" else "non-copyright"} text.")
   Logger.info("Generation of our own footnotes is ${if (!okToGenerateFootnotes) "NOT" else ""} permitted.")
   Logger.info("Versification scheme is ${versificationScheme ?: "STEP-internal"}.")
   Logger.info("ReversificationType is $reversificationType")
   Logger.info("osis2mod type is $osis2modType with STEP software version specified as $stepSoftwareVersionRequired.")
   Logger.info("Target audience is $targetAudience.")
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                       Copyright vs non-copyright                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doCopyrightText ()
  {
    setSamificationIfNecessary()
    setCopyrightLimitations()
    setEncryption() // Always do this last -- it sets the target audience, and we need to make sure its setting prevails over any others.
  }


  /****************************************************************************/
  private fun doNonCopyrightText ()
  {
    setNonCopyrightFreedoms()

    if (isConversionTimeRestructuring())
      setConversionTimeRestructuring()
    else
     setSamificationIfNecessary()
  }


  /****************************************************************************/
  private fun isConversionTimeRestructuring (): Boolean
  {
    val x = ConfigData.get("stepReversificationType", "none").lowercase(); ConfigData.deleteAndPut("stepReversificationType", x, force = true)
    return "conversiontime" == x
  }


  /****************************************************************************/
  /* At present we're saying that we must not generate any footnotes of our
     own.  I have to say I'm not entirely convinced about this.  The rationale
     for suppressing the footnotes is that this may well be outlawed by licence
     conditions.  But we are already making changes (even without
     reversification) because we always fill in any empty verses which may be
     required, and we always expand elisions and restructure tables.
     Suppressing the footnotes doesn't get rid of those changes -- it merely
     prevents us from explaining why they were made. */

  private fun setCopyrightLimitations ()
  {
    ConfigData.deleteAndPut("stepOkToGenerateFootnotes", "no", force = true)
  }


  /****************************************************************************/
  /* It's always ok to generate footnotes, and the reversification footnote
     level defaults to basic. */

  private fun setNonCopyrightFreedoms ()
  {
    ConfigData.deleteAndPut("stepOkToGenerateFootnotes", "yes", force = true)
    val x = ConfigData.get("stepReversificationFootnoteLevel", "basic"); ConfigData.deleteAndPut("reversificationFootnoteLevel", x, force = true)
  }


  /****************************************************************************/
  /* Record that we definitely want to encrypt; and if we do that, the target
     audience must be STEP-only. */

  private fun setEncryption ()
  {
    ConfigData.deleteAndPut("stepEncryptionRequired", "yes", force = true)
    ConfigData.deleteAndPut("stepTargetAudience", "S", force = true)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Reversification options                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* If we want to apply conversion-time restructuring to a text:

     - We have to set stepReversificationType to reflect this.

      - We need to ensure that we use Crosswire's osis2mod.

      - We need to flag the fact that version 1 of the offline STEP software
        will be good enough.

      - The target scheme will be NRSV or NRSV(A) as appropriate.

      - The module can be used both within STEP and for a public audience.
  */

  private fun setConversionTimeRestructuring ()
  {
    ConfigData.deleteAndPut("stepReversificationType", "conversiontime", force = true)
    ConfigData.deleteAndPut("stepOsis2modType", "crosswire", force = true)
    ConfigData.deleteAndPut("stepSoftwareVersionRequired", 1.toString(), force=true)
    ConfigData.deleteAndPut("stepVersificationScheme", m_NrsvScheme, force = true)
    ConfigData.deleteAndPut("stepTargetAudience", "PS", force = true)
  }


  /****************************************************************************/
  /* If we want to apply conversion-time restructuring to a text:

     - We have to set stepReversificationType to reflect this.

     - We need to ensure that we use Crosswire's osis2mod.

     - We need to flag the fact that version 1 of the offline STEP software
       will be good enough.
    */

   private fun setNoReversificationActionRequired ()
   {
     ConfigData.deleteAndPut("stepReversificationType", "none", force = true)
     ConfigData.deleteAndPut("stepOsis2modType", "crosswire", force = true)
     ConfigData.deleteAndPut("stepSoftwareVersionRequired", 1.toString(), force=true)
     ConfigData.deleteAndPut("stepTargetAudience", "PS", force = true)
   }


  /****************************************************************************/
  /* If we want to samify a text:

     - We have to set stepReversificationType to reflect this.

     - We need to ensure that we use our own osis2mod.

     - We need to flag the fact that we need at least version 2 of the STEP
       offline module to use the module.

     - With a samified module, the target audience must be STEP only.

     - We delete any existing proposed versification scheme, because the
       processing creates its own ad hoc one later.
   */

  private fun setSamification ()
  {
    ConfigData.deleteAndPut("stepReversificationType", "runtime", force = true)
    ConfigData.deleteAndPut("stepOsis2modType", "step", force = true)
    ConfigData.deleteAndPut("stepSoftwareVersionRequired", 2.toString(), force=true)
    ConfigData.deleteAndPut("stepTargetAudience", "S", force = true)
    ConfigData.delete("stepVersificationScheme")
  }


  /****************************************************************************/
  /* Records that we do, or do not, need to samify, according to whether the
     text fits with NRSV(A).

     Actually, currently I'm being asked to samify absolutely _everything_,
     even if it NRSV(A)-compliant.  The rationale is that if we do this, then
     we have a uniform and simple way of applying reversification-related
     tweaks to things (by making changes to the samification JSON file).

     I have to say I'm not at all sure about this, on quite a number of grounds:

     - Applying tweaks just ain't that easy.  It's not just a case of changing
       the OSIS -- you then have to update history and version information in
       both step.conf and the Sword config file; you have to decide whether to
       use a new encryption key (and update the config file if you do); you have
       to work out what the osis2mod command line should look like and run it;
       you have to create the module zip file and the repository zip file; and
       throughout you have to make sure that all of the names continue to marry
       up.

     - Samifying a text means that existing offline STEP users will _have_ to
       upgrade their software, because samified texts can't be used with old
       software.

     - If the text is open access, we'll need to have _two_ versions of the
       module, one for public access, and one for use within STEP.  If we
       didn't samify, one version would do.

     - Having two versions of the module also means two different folder
       structures to update each time any change is made.

     - And I can't actually see what benefit accrues in this case, because
       we know a priori that the text is NRSV(A)-compliant (or deviates
       only in a good way), so samification shouldn't actually be doing
       anything anyway.
  */

  private fun setSamificationIfNecessary ()
  {
    if (m_FitsWithNrsv && false) // $$$
    {
      ConfigData.deleteAndPut("stepVersificationScheme", m_NrsvScheme, force = true)
      setNoReversificationActionRequired()
    }
    else
      setSamification()
  }


  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Included speculatively.  Returns OK or BAD depending upon whether this
     text fits with NRSV(A), or at most deviates from it only in that NRSV(A)
     expects verses which the text does not supply. */

  private fun fitsWithNrsv (bibleStructureUnderConstruction: BibleStructure) =
    PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.VersificationDeviationType.BAD != PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleScheme(m_NrsvScheme, bibleStructureUnderConstruction).getDeviationType()


  /****************************************************************************/
  private fun getCanonicalisedVersificationSchemeIfAny ()
  {
    m_ForcedVersificationScheme = ConfigData["stepVersificationScheme"] ?: return
    m_ForcedVersificationScheme = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(m_ForcedVersificationScheme!!)
    ConfigData.deleteAndPut("stepVersificationScheme", m_ForcedVersificationScheme!!, force = true)
  }


  /****************************************************************************/
  private var m_FitsWithNrsv = false // True if versification exactly matches NRSV(A), or deviates only in a good way.
  private var m_ForcedVersificationScheme: String? = null // Non-null if a versification scheme has been overtly specified.
  private var m_IsCopyrightText = false
  private lateinit var m_NrsvScheme: String // NRSV or NRSVA, depending upon whether or not the text contains DC books.  Note there is no implication that the text actually _fits_ NRSV(A); merely that if it did, this is the version we'd need.
}
