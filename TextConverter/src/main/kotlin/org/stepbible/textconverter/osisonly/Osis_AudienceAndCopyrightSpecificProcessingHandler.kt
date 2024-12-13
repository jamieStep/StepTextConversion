/******************************************************************************/
package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.quotify
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.quotifyIfContainsSpaces
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler_ConversionTime
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler_RunTime
import java.io.File
import java.util.ArrayList


/******************************************************************************/
/**
* Some processing has to reflect not the text itself, but its environment --
* is it copyright or not, are we creating a STEP-only module or a public one,
* or whatever.  This class uses configuration information to determine what
* this processing should entail.
*
* <span class='important'>IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT * IMPORTANT</span>
*
* We had intended to support three kinds of reversification -- 'none',
* run-time and conversion-time.  'none' is ok (this would be used when creating
* a public module).  Run-time is ok (this is what we use for all STEP-
* internal modules).  Conversion-time is more of an issue, however.
*
* We never anticipated using this much anyway -- it entails physically
* restructuring modules during the conversion process, and licence
* conditions will normally preclude that.  Code does exist to handle it,
* but be aware that at the time of writing, it has never been exercised.
*
* The processing here is driven by a few configuration parameters.  A
* previous incarnation of this class forced these parameters into
* canonical form, worked out what to do if any of them was not supplied,
* and then forcibly updated the configuration data to reflect all of this.
* It then left the rest of the processing to make use of the configuration
* data as it saw fit.
*
* On maturer reflection, this is not ideal -- it is, I think, better for the
* class to decide not upon what the configuration parameters should look like,
* but upon what processing is required.  There is a slight problem here, in
* that the relevant configuration parameters were previously being used quite
* widely.  Ideally, once I've decided on the relevant processing here, I think
* I should probably delete the configuration parameters so as to ensure that
* nothing else is going to use them to make processing decisions which might
* conflict with the ones made here.  Unfortunately the configuration parameters
* are also used in the final outputs -- for example driving decisions over
* filenames and turning up in comments in various files.  In view of this, at
* present I think removing them would be awkward, although it might be useful
* to consider doing that at some point in the future.  The upshot is that as
* well as using the configuration parameters to drive decisions here, I also
* leave them in canonical form for the rest of the processing to use, and
* just hope that the things it does do not conflict with decisions taken
* here.
*
* @author ARA "Jamie" Jamieson
*/

object Osis_AudienceAndCopyrightSpecificProcessingHandler: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun doReversificationIfNecessary (dataCollection: X_DataCollection)
  {
    if (null != m_ReversificationHandler)
      m_ReversificationHandler!!.process(dataCollection)
  }
  
  
  /****************************************************************************/
  fun getReversificationHandler () = m_ReversificationHandler



  /****************************************************************************/
  fun invokeOsis2mod ()
  {
    handleOsis2modCall()
  }
  
  
  /****************************************************************************/
  private var m_NeedEncryption = false
  private var m_ReversificationHandler: PA_ReversificationHandler? = null
  private lateinit var m_VersificationScheme: String



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
  /* Hmm ... this gets rather complicated, as will shortly be apparent. */

  private fun doIt ()
  {
    /**************************************************************************/
    Rpt.report(level = 1, "Determining reversification requirements etc.")



    /**************************************************************************/
    /* Canonicalise target audience.  This determines the reversification
       type.  If we are applying reversification, then I'm happy to accept and
       ignore any forced versification scheme.  If we are not applying
       reversification, we must have a forced versification scheme, and I also
       need to canonicalise it.  We'll check that shortly. */

    val targetAudience = ConfigData["stepTargetAudience"]!!.lowercase(); ConfigData.deleteAndPut("stepTargetAudience", targetAudience, true)
    val reversificationType = getReversificationType(targetAudience)
    m_VersificationScheme = getCanonicalisedVersificationSchemeIfAny()




    /**************************************************************************/
    val isCopyrightText: Boolean
    when (if (null == ConfigData["stepIsCopyrightText"]) null else ConfigData.getAsBoolean("stepIsCopyrightText"))
    {
      true ->
      {
        if ("public" == targetAudience)
          throw StepExceptionWithoutStackTraceAbandonRun("Target audience cannot be Public if the text is marked as being copyright.")
        isCopyrightText = true
      }
      
      false ->
        isCopyrightText = false // If the text isn't copyright, there's no need to check if we're doing a public or STEP build: either is possible.
        
      null ->
      {
        if ("public" == targetAudience)
          throw StepExceptionWithoutStackTraceAbandonRun("If target audience is Public you must overtly set stepIsCopyrightText to confirm this is not a copyright text.")
        isCopyrightText = false // Assume we have a non-copyright text if we're creating a public module.
      }  
    }

    ConfigData.deleteAndPut("stepIsCopyrightText", if (isCopyrightText) "yes" else "no", force = true) // Public texts can't be copyright, and vice-versa.



    /**************************************************************************/
    /* A bit of validation.  This may actually implicitly simply repeat some
       of the validation above, but better safe than sorry. */

    when (reversificationType)
    {
      "none" -> // No reversification.
      {
        if ("step" == targetAudience)
          throw StepExceptionWithoutStackTraceAbandonRun("Must apply run-time reversification when the target audience is STEP.")

        if ("public" == targetAudience && m_VersificationScheme.isEmpty())
          throw StepExceptionWithoutStackTraceAbandonRun("If you are building a public module without reversification, you must specify a Crosswire versification scheme.")
      }

      "runtime" ->
      {
        ConfigData.delete("stepVersificationScheme") // Run-time reversification doesn't need to be given a versification scheme.

        if ("public" == targetAudience)
          throw StepExceptionWithStackTraceAbandonRun("Applying run-time versification, so you can't create a public module.")
      }

      "conversiontime" ->
      {
        // It's possible conversion-time reversification isn't properly implemented, but we'll leave it to the conversion-time code to make that call.
        // Not sure whether we need this to be public texts only -- certainly it doesn't really make sense to put this through Sami's code, since that's
        // concerned with dealing with non-KJVA-compliant texts, and the whole point of doing conversion-time reversification is to ensure we end up
        // with a text which _is_ compliant.
      }
    }



    /**************************************************************************/
    when (reversificationType)
    {
      "none"           -> setNoReversificationActionRequired()
      "runtime"        -> setRunTimeReversification()
      "conversiontime" -> setConversionTimeRestructuring(m_VersificationScheme)
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
   val stepSoftwareVersionRequired = ConfigData["stepSoftwareVersionRequired"]
   val versificationScheme = ConfigData["stepVersificationScheme"]
   Logger.info("Treating this as a ${if (isCopyrightText) "copyright" else "non-copyright"} text.")
   Logger.info("Generation of our own footnotes is${if (!okToGenerateFootnotes) " NOT" else ""} permitted.")
   Logger.info("Versification scheme is ${versificationScheme ?: "STEP-internal"}.")
   Logger.info("ReversificationType is $reversificationType")
   Logger.info("Target audience is $targetAudience.")
   Logger.info("Version of STEP runtime software required is $stepSoftwareVersionRequired.")
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
  /* Decides what reversification facilities to use, if any. */

  private fun getReversificationType (targetAudience: String): String
  {
    return if ("step" == targetAudience) // Always use run-time if we're targetting STEPBible.
      "runtime"
    else if (ConfigData.getAsBoolean("stepConversionTimeReversification", "no")) // Otherwise use conversion-time if that has been requested.
      "conversiontime"
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
    m_NeedEncryption = wantEncryption
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

      - The target scheme will be KJV or KJV(A) as appropriate.
  */

  private fun setConversionTimeRestructuring (versificationScheme: String)
  {
    ConfigData.deleteAndPut("stepReversificationType", "conversiontime", force = true)
    ConfigData.deleteAndPut("stepVersified", "No", force = true)
    ConfigData.deleteAndPut("stepVersificationScheme", versificationScheme, force = true)
    m_ReversificationHandler = PA_ReversificationHandler_ConversionTime
  }


  /****************************************************************************/
  /* If we want to apply conversion-time restructuring to a text:

     - We have to set stepReversificationType to reflect this.

     - We need to flag the fact that version 1 of the offline STEP software
       will be good enough.
    */

   private fun setNoReversificationActionRequired ()
   {
     ConfigData.deleteAndPut("stepReversificationType", "none", force = true)
     ConfigData.deleteAndPut("stepVersified", "No", force = true)
     ConfigData.deleteAndPut("stepSoftwareVersionRequired", 1.toString(), force=true)
     m_ReversificationHandler = null
   }


  /****************************************************************************/
  /* If we want to samify a text:

     - We have to set stepReversificationType to reflect this.

     - We need to flag the fact that we need at least version 2 of the STEP
       offline module to use the module.

     - With a samified module, the target audience must be STEP only.

     - We delete any existing proposed versification scheme, because the
       processing creates its own ad hoc one later.
   */

  private fun setRunTimeReversification ()
  {
    ConfigData.deleteAndPut("stepReversificationType", "runtime", force = true)
    ConfigData.deleteAndPut("stepTargetAudience", "step", false)
    ConfigData.deleteAndPut("stepVersified", "Yes", force = true)
    ConfigData.deleteAndPut("stepSoftwareVersionRequired", 2.toString(), force=true)
    ConfigData.deleteAndPut("stepVersificationScheme", ConfigData["stepModuleName"]!!, true) // Force to our own name for the versification scheme.
    ConfigData.delete("stepVersificationScheme")
    m_ReversificationHandler = PA_ReversificationHandler_RunTime
  }
  
  
  /****************************************************************************/
  /* Obtains encryption data.  (This method is called only if encryption is
     actually needed.)

     There are two separate pieces of encryption data.  One is a longish
     random password which is passed to osis2mod; and the other is an
     encrypted form of this, which is stored in a special configuration file
     used by JSword when decrypting data.

     With offline STEP on Windows, this file needs to go into a special location
     within the user's home folder, and I do this here in the converter so as
     to avoid having to move it around manually.  With online STEP, I presume it
     also needs to go into a special location, but I don't know where that is.

     (I have a feeling that in fact it should go into the module's zip file, and
     that it will automatically be moved to the right place when the module is
     installed, but I remain unclear quite how to achieve that.)

     I also store a copy of this configuration file in the Metadata folder so
     that I can locate it easily and pass it to other people --
     Metadata/<moduleName>.conf. */

  private fun encryptionDataHandler (filePath: String): String?
  {
    /**************************************************************************/
    if (!m_NeedEncryption)
      return null



    /**************************************************************************/
    val osis2modEncryptionKey = MiscellaneousUtils.generateRandomString(64)
    ConfigData.put("stepOsis2ModEncryptionKey", osis2modEncryptionKey, true)
    val obfuscationKey = "p0#8j..8jm@72k}28\$0-,j[\$lkoiqa#]" // Fixed for all time.
    val stepEncryptionKey = MiscellaneousUtils.generateStepEncryptionKey(osis2modEncryptionKey, obfuscationKey)



    /**************************************************************************/
    /* Write the details to the file which controls encryption. */

    val writer = File(filePath).bufferedWriter()
    writer.write("[${ConfigData["stepModuleName"]!!}]"); writer.write("\n")
    writer.write("CipherKey=$stepEncryptionKey");        writer.write("\n")
    writer.write("STEPLocked=true");                     writer.write("\n")
    writer.close()



    /**************************************************************************/
    return osis2modEncryptionKey
  }


  /****************************************************************************/
  private fun handleOsis2modCall ()
  {
    /**************************************************************************/
    /* Generate the JSON needed by our version of osis2mod if this is a runtime
       reversification run. */

    val isRunTimeReversification = PA_ReversificationHandler_RunTime === m_ReversificationHandler
    if (isRunTimeReversification)
      Osis_Osis2modRunTimeReversificationJsonHandler.process(m_ReversificationHandler!!)



    /**************************************************************************/
    /* Since there are occasions when we need to be able to run the relevant
       osis2mod command manually from the command line, and since it is always
       a pain to work out what the command line should look like, here is the
       information you need ...

         osis2mod.exe <outputPath> <osisFilePath> -v <versificationScheme> -z -c "<password>"

       where <outputPath> is eg ...\Sword\modules\texts\ztext\NIV2011 and
       <password> is a random string of letters, digits and selected special
       characters which is used as a password / encryption key when generating
       the module.

       You can optionally add '> logFile' to the end to redirect output.  And
       perhaps more useful is '> logfile 2>&1' which redirects both stdout and
       stderr to the same file.  But do that only if you are running direct from
       a command line, not if you are using the code here to run things under
       control of the converter -- there are problems with system utilities which
       mean this doesn't work properly -- see head-of-method comments to
       runCommand.

       Re enclosing the program path in quotes below ...

       Enclosing the path in quotes seems to make sense if you think in terms of
       the command actually being expanded into something which is then run as
       though from the command line -- otherwise, if the path contains spaces,
       things won't be handled correctly.

       And if you do that on Windows, things are indeed fine.  But not on Linux,
       where things work only if you do _not_ enclose the path in quotes.

       In fact, I get the impression that under the hood the processing _isn't_
       actually just creating a command line, but instead is doing something
       special with the first element -- and quotes get in the way of that on
       Linux.

       I have therefore stopped adding the quotes.  What I now have seems to
       work on both Windows and Linux -- although I have to admit I suspect
       that on neither platform have we actually had a path which contained
       spaces. */

    val programPath = ConfigData["stepOsis2modFilePath"]!!
    val swordExternalConversionCommand: MutableList<String> = ArrayList()
    swordExternalConversionCommand.add(programPath) // Don't enclose the path in quotes -- see note above.
    swordExternalConversionCommand.add(FileLocations.getSwordTextFolderPath())
    swordExternalConversionCommand.add(FileLocations.getInternalOsisFilePath())

    if (isRunTimeReversification)
    {
      swordExternalConversionCommand.add("-V")
      swordExternalConversionCommand.add(FileLocations.getOsis2ModSupportFilePath())
    }
    else
    {
      swordExternalConversionCommand.add("-v")
      swordExternalConversionCommand.add(m_VersificationScheme)
    }

    swordExternalConversionCommand.add("-z")

    val osis2modEncryptionKey = encryptionDataHandler(FileLocations.getEncryptionDataFilePath())
    if (null != osis2modEncryptionKey)
    {
      swordExternalConversionCommand.add("-c")
      swordExternalConversionCommand.add(osis2modEncryptionKey)
    }



    /**************************************************************************/
    /* If we have any grounds at all for giving up, now would be a good time to
       do it, before bothering with the remaining processing. */

    Logger.announceAll(true)



    /**************************************************************************/
    /* Sometimes -- under circumstances I cannot fathom -- osis2mod hangs when
       run under control of the converter (either as a JAR or from the IDE).
       It seems to be something to do with the fact that it is generating
       output to explain changes it has made; but then it always generates
       _some_ output, if only to explain that it has succeeded, so I can't see
       that generating explanatory output _can_ be the issue.

       Under these circumstances, the only workaround I can find is to copy
       the command line which I would like to run to the clipboard, and then
       run it manually in a command window before permitting the converter to
       continue and create the repository package.

       In fact, to date this has been an issue only when creating public modules
       -- STEP versions seem to work ok (probably because they don't generate
       this output).

       Given that it may be convenient to record in step.conf that manual
       operation is required, and given that where a text can be used to
       generate both a public and a STEP module, step.conf will be shared
       by both, I take note of the manual osis2mod request only if this is
       not a STEP run. */

    if (ConfigData.getAsBoolean("stepManualOsis2mod") && "step" != ConfigData["stepTargetAudience"])
    {
      val commandAsString = swordExternalConversionCommand.joinToString(" "){ quotifyIfContainsSpaces(it) } + " > ${quotify(FileLocations.getOsisToModLogFilePath())} 2>&1"
      MiscellaneousUtils.copyTextToClipboard(commandAsString)
      println("")
      println("The command to run osis2mod has been copied to the clipboard.  Open a plain vanilla command window and run it from there.")
      println("In case you need it, it is ...  $commandAsString")
      print("Hit ENTER here when osis2mod has completed: "); readlnOrNull()
    }
    else
    {
      val rc = runCommand("Running external command to generate Sword data: ", swordExternalConversionCommand, errorFilePath = FileLocations.getOsisToModLogFilePath())
      ConfigData["stepOsis2modReturnCode"] = rc.toString()
      Rpt.report(level = 1, "osis2mod completed")
    }
  }
}
