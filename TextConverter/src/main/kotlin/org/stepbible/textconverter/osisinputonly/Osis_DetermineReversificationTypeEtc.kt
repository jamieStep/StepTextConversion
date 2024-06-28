/******************************************************************************/
package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.utils.BibleStructure
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.stepexception.StepException


/******************************************************************************/
/**
* Determines reversification and osis2mod types etc.  This has turned out to be
* remarkably difficult to pin down.  Comments deferred to later, so I can put
* them adjacent to the code which may change again.
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
  fun process (bibleStructureUnderConstruction: BibleStructure) = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* At one point I was hoping, with open access texts which were
     NRSV-compliant, to be able to build just a single module which could be
     made publicly available and also used within STEP.  And this looked to be
     something which might be feasible with quite a number of modules, because
     lots of them looked to be NRSV-compliant, or to deviate only in a 'good'
     way.

     However, I then discovered that the Crosswire header file for NRSV is
     wrong.  (Wrong certainly in that it contains 2Cor 13:14, where NRSV does
     not -- and possibly, for all I know, wrong elsewhere too.)  This meant
     that most or all of the texts which had appeared to be compliant in fact
     were not.  And since DIB reckons that an awful lot of texts are based
     upon NIV (which itself is not NRSV compliant), the idea of shared modules
     looks like a forlorn hope.  In view of this, I have dropped the attempts
     in this present class to determine whether texts are compliant or not. */

  /****************************************************************************/
  /* Hmm ... this gets rather complicated, as will shortly be apparent. */

  private fun doIt ()
  {
    /**************************************************************************/
    Dbg.reportProgress("Determining reversification requirements etc.")



    /**************************************************************************/
    /* The parameters which control what we're going to do. */

    val isCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText")
    val forcedVersificationScheme = getCanonicalisedVersificationSchemeIfAny() // Check if we've been given a scheme, and if so, convert the name to canonical form.
    var reversificationType = getReversificationType()
    val targetAudience = ConfigData["stepTargetAudience"]!!



    /**************************************************************************/
    /* We always samify modules destined for use in STEP, unless we are doing
       runtime restructuring. */

    if ("S" in targetAudience && "none" == reversificationType)
      reversificationType = "runtime"



    /**************************************************************************/
    /* A bit of validation. */

    if ("P" in targetAudience && isCopyrightText)
      throw StepException("Can't create a public module for copyright text")

    if (isCopyrightText && "conversiontime" == reversificationType)
      throw StepException("Can't apply conversion-time reversification (ie physical restructuring) to a copyright text.")

    when (reversificationType)
    {
      "none" ->
      {
        if ("S" in targetAudience && !forcedVersificationScheme.startsWith("NRSV"))
          throw StepException("Not reversifying, so you must specify NRSV or NRSVA as the versification scheme when creating a module for use in STEPBible.")

        if ("P" in targetAudience && forcedVersificationScheme.isEmpty())
          throw StepException("If you are building a public module without reversification, you must specify a Crosswire versification scheme.")
      }

      "runtime" ->
      {
        if (forcedVersificationScheme.isNotEmpty())
          throw StepException("Applying samification, so you must not specify a versification scheme.")

        if ("P" in targetAudience)
          throw StepException("Applying samification, so you can't create a public module.")
      }

      "conversiontime" ->
      {
        if (!forcedVersificationScheme.startsWith("NRSV"))
          throw StepException("Applying run-time reversification, so you must specify NRSV or NRSVA as the versification scheme.")
      }
    }



    /**************************************************************************/
    when (reversificationType)
    {
      "none"           -> setNoReversificationActionRequired()
      "runtime"        -> setSamification()
      "conversiontime" -> setConversionTimeRestructuring(forcedVersificationScheme)
    }



    /**************************************************************************/
    /* At this juncture we could split the processing up in any number of
       different ways, depending upon the control parameters just obtained.
       However, the issue of whether or not it is a copyright text is probably
       the most important, because it has external (legal) implications. */

    if (isCopyrightText)
    {
      setCopyrightLimitations() // There are certain things -- like generating footnotes -- which we assume we aren't allowed to do.
      setEncryption()           // Copyright text always has to be encrypted.
    }
    else
    {
      setFreedomsForNonCopyrightTexts()
    }



   /**************************************************************************/
   /* Report what we're doing. */

   val okToGenerateFootnotes = ConfigData.getAsBoolean("stepOkToGenerateFootnotes")
   val osis2modType = ConfigData["stepOsis2modType"]!!
   val stepSoftwareVersionRequired = ConfigData["stepSoftwareVersionRequired"]
   val versificationScheme = ConfigData["stepVersificationScheme"]
   Logger.info("Treating this as a ${if (isCopyrightText) "copyright" else "non-copyright"} text.")
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
  private fun getCanonicalisedVersificationSchemeIfAny (): String
  {
    var res = ConfigData["stepVersificationScheme"] ?: return ""
    res = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(res)
    ConfigData.deleteAndPut("stepVersificationScheme", res, force = true)
    return res
  }


  /****************************************************************************/
  /* Canonicalises the value and returns it. */

  private fun getReversificationType (): String
  {
    var x = ConfigData.get("stepReversificationType", "none").lowercase()
    if (x.isEmpty()) x = "none"
    ConfigData.deleteAndPut("stepReversificationType", x, force = true)
    return x
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

  private fun setCopyrightLimitations () = ConfigData.deleteAndPut("stepOkToGenerateFootnotes", "no", force = true)


  /****************************************************************************/
  /* Record that we definitely want to encrypt. */

  private fun setEncryption (): Boolean
  {
    ConfigData.deleteAndPut("stepEncryptionRequired", "yes", force = true)
    return ConfigData.getAsBoolean("stepEncryptionRequired")
  }


  /****************************************************************************/
  /* It's always ok to generate footnotes, and the reversification footnote
     level defaults to basic. */

  private fun setFreedomsForNonCopyrightTexts ()
  {
    ConfigData.deleteAndPut("stepOkToGenerateFootnotes", "yes", force = true)
    val x = ConfigData.get("stepReversificationFootnoteLevel", "basic"); ConfigData.deleteAndPut("reversificationFootnoteLevel", x, force = true)
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

  private fun setConversionTimeRestructuring (versificationScheme: String)
  {
    ConfigData.deleteAndPut("stepReversificationType", "conversiontime", force = true)
    ConfigData.deleteAndPut("stepOsis2modType", "crosswire", force = true)
    ConfigData.deleteAndPut("stepSoftwareVersionRequired", 1.toString(), force=true)
    ConfigData.deleteAndPut("stepVersificationScheme", versificationScheme, force = true)
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
    ConfigData.delete("stepVersificationScheme")
  }

}
