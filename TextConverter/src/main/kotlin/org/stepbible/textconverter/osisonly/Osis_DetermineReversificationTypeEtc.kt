/******************************************************************************/
package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase


/******************************************************************************/
/**
* Determines reversification and osis2mod types etc.  This has turned out to be
* remarkably difficult to pin down.  Comments deferred to later, so I can put
* them adjacent to the code which may change again.
*
* <span class='important'>IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT</span>
*
* We had intended to support three kinds of reversification -- 'none',
* run-time and conversion-time.  'none' is ok (this would be used when creating
* a public module).  Run-time is ok (this is what we use for all STEP-
* internal modules).  Conversion-time is more problematical, however.
*
* We never anticipated using this much anyway -- it entails physically
* restructuring modules during the conversion process, and licence
* conditions will normally preclude that.  But the problem is that we'd be
* restructuring them to align them with NRSV, and we'd thought that could
* then be put through the Crosswire osis2mod.  However, an NRSV-compliant
* text does not fit with Crosswire's NRSV scheme, because that scheme is
* not, itself, NRSV-compliant.  This means that physically restructured
* texts would still need to be samified, and the processing to do that
* still needs sorting out.
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
  fun process () = doIt()





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
    Dbg.reportProgress("\nDetermining reversification requirements etc.")



    /**************************************************************************/
    /* The parameters which control what we're going to do. */

    val forcedVersificationScheme = getCanonicalisedVersificationSchemeIfAny() // Check if we've been given a scheme, and if so, convert the name to canonical form.
    val targetAudience = ConfigData["stepTargetAudience"]!!.first().uppercase()
    val reversificationType = getReversificationType(targetAudience)

    val isCopyrightText: Boolean
    when (if (null == ConfigData["stepIsCopyrightText"]) null else ConfigData.getAsBoolean("stepIsCopyrightText"))
    {
      true ->
      {
        if ("P" == targetAudience)
          throw StepExceptionBase("Target audience cannot be Public if the text is marked as being copyright.")
        isCopyrightText = true
      }
      
      false ->
        isCopyrightText = false // If the text isn't copyright, there's no need to check if we're doing a public or STEP build: either is possible.
        
      null ->
      {
        if ("P" == targetAudience)
          throw StepExceptionBase("If target audience is Public you must overtly set stepIsCopyrightText to confirm this is not a copyright text.")
        isCopyrightText = "S" == targetAudience // Assume we have a copyright text if creating a STEP-only module.
      }  
    }

    ConfigData.deleteAndPut("stepIsCopyrightText", if (isCopyrightText) "yes" else "no", force = true) // Public texts can't be copyright, and vice-versa.



    /**************************************************************************/
    /* A bit of validation.  This may actually implicitly simply repeat some
       of the validation above, but better safe than sorry. */

    when (reversificationType)
    {
      "none" ->
      {
        if ("S" == targetAudience)
          throw StepExceptionBase("Must apply run-time reversification when the target audience is STEP.")

        if ("P" == targetAudience && forcedVersificationScheme.isEmpty())
          throw StepExceptionBase("If you are building a public module without reversification, you must specify a Crosswire versification scheme.")
      }

      "runtime" ->
      {
        ConfigData.delete("stepVersificationScheme")

        if ("P" == targetAudience)
          throw StepExceptionBase("Applying samification, so you can't create a public module.")
      }

      "conversiontime" ->
      {
        throw StepExceptionBase("I'm not actually sure we can do conversion-time reversification: we can't convert to Crosswire NRSV, because that's not NRSV-compliant.")
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
      setEncryption(true)       // Copyright text always has to be encrypted.
    }
    else
    {
      setFreedomsForNonCopyrightTexts() // Things you're allowed to do on non-copyright texts.
      setEncryption(false)              // Copyright text always has to be encrypted.
   }



   /**************************************************************************/
   /* Report what we're doing. */

   val okToGenerateFootnotes = ConfigData.getAsBoolean("stepOkToGenerateFootnotes")
   val osis2modType = ConfigData["stepOsis2modType"]!!
   val stepSoftwareVersionRequired = ConfigData["stepSoftwareVersionRequired"]
   val versificationScheme = ConfigData["stepVersificationScheme"]
   Logger.info("Treating this as a ${if (isCopyrightText) "copyright" else "non-copyright"} text.")
   Logger.info("Generation of our own footnotes is${if (!okToGenerateFootnotes) " NOT" else ""} permitted.")
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

  private fun getReversificationType (targetAudience: String): String
  {
    return if (ConfigData.getAsBoolean("stepConversionTimeReversification", "no"))
      "conversiontime"
    else if ("S" == targetAudience)
      "runtime"
    else
      "none"
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

  private fun setEncryption (wantEncryption: Boolean): Boolean
  {
    ConfigData.deleteAndPut("stepEncrypted", if (wantEncryption) "Yes" else "No", force = true)
    return ConfigData.getAsBoolean("stepEncrypted")
  }


  /****************************************************************************/
  /* It's always ok to generate footnotes, and the reversification footnote
     level defaults to basic. */

  private fun setFreedomsForNonCopyrightTexts ()
  {
    ConfigData.deleteAndPut("stepOkToGenerateFootnotes", "yes", force = true)
    val x = ConfigData.get("stepReversificationFootnoteLevel", "basic"); ConfigData.deleteAndPut("stepReversificationFootnoteLevel", x, force = true)
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
  */

  private fun setConversionTimeRestructuring (versificationScheme: String)
  {
    throw StepExceptionBase("Needs more thought.  We thought we could do conversion-time restructuring and then use Crosswire osis2mod.  But we can't, because we'd have to target NRSV, and Crosswire NRSV isn't NRSV-compliant.")
//    ConfigData.deleteAndPut("stepReversificationType", "conversiontime", force = true)
//    ConfigData.deleteAndPut("stepOsis2modType", "crosswire", force = true)
//    ConfigData.deleteAndPut("stepVersified", "No", force = true)
//    ConfigData.deleteAndPut("stepVersificationScheme", versificationScheme, force = true)
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
     ConfigData.deleteAndPut("stepVersified", "No", force = true)
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
    ConfigData.deleteAndPut("stepVersified", "Yes", force = true)
    ConfigData.deleteAndPut("stepSoftwareVersionRequired", 2.toString(), force=true)
    ConfigData.delete("stepVersificationScheme")
  }

}
