package org.stepbible.textconverter.support.miscellaneous

import org.jasypt.util.text.BasicTextEncryptor
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File


/******************************************************************************/
/**
 * Miscellaneous utilities.
 *
 * @author ARA "Jamie" Jamieson
 */


object MiscellaneousUtils
 {
    /**********************************************************************************************************************/
    /**
    * Checks whether a list of items is arranged in strictly increasing order.
    *
    * @param data
    * @return True if strictly increasing.
    */

    fun <T: Comparable<T>> checkInStrictlyAscendingOrder (data: List<T>): Boolean
    {
      for (i in 1 ..< data.size)
        if (data[i] <= data[i - 1]) return false
      return true
    }


    /**********************************************************************************************************************/
    /**
    * Converts 1, 2, ... 26, 27, 28 ... to a, b, ... z, aa, ab ...
    *
    * @param n Number to be converted
    * @param lowChar First character in range of available characters.
    * @param highChar Last character in range of available characters.
    * @return Character string.
    */

    fun convertNumberToRepeatingString (n: Int, lowChar: Char, highChar: Char): String
    {
       val lowCharCode = lowChar.code
       val nElts = highChar.code - lowCharCode + 1
       var x = n
       var res = ""
       while (x > 0)
       {
         res = (lowCharCode + (x - 1) % nElts).toChar().toString() + res
         x = (x - 1) / nElts
       }

       return res
    }


    /**********************************************************************************************************************/
    /**
     * Converts eg a-z to 1-26, aa to 27, ab to 28, etc.
     *
     * @param text: String to be converted.
     * @param lowerBound Lowest character in range.
     * @param upperBound Highest character in range.
     * @return Converted value.
     */

    fun convertRepeatingStringToNumber (text: String, lowerBound: Char, upperBound: Char): Int
    {
        val lowerBoundCode = lowerBound.code
        val range = upperBound.code - lowerBoundCode + 1
        var res = 0
        text.forEach {
            val n = it.code - lowerBoundCode + 1
            if (n < 1 || n > range) throw StepException("convertRepeatingStringToNumber: Bad text: $text")
            res = range * res + n
        }

        return res
    }


    /**********************************************************************************************************************/
    /**
     * A do-nothing method for use eg in if statements where we want to do nothing, but want to make it clear that this was
     * a deliberate choice.
     */

    inline fun doNothing (@Suppress("UNUSED_PARAMETER") vararg x: Any) {}


    /****************************************************************************/
    /**
    * Generates the encrypted password needed by JSword to decrypt things.  For
    * more information about encryption, refer to TextConverterProcessorOsisToSword.
    *
    * @param osis2modPassword The password which was fed to osis2mod.
    * @param stepObfuscationKey The key used when obfuscating the password.
    * @return Obfuscated password.
    */

    fun generateStepEncryptionKey (osis2modPassword: String, stepObfuscationKey: String): String
    {
      val bte = BasicTextEncryptor()
      bte.setPassword(stepObfuscationKey)
      return bte.encrypt(osis2modPassword)
    }


    /****************************************************************************/
    /**
    * Generates a string nChars in length made up of a random collection of lower
    * case letters, upper case letters, digits and a set of special characters.
    *
    * @param nChars Number of characters required.
    */

    fun generateRandomString (nChars: Int): String
    {
      return List(nChars) { C_generateRandomString_Chars.random() }.joinToString("")
    }

    private val C_generateRandomString_Chars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '$', '%', '_', '#', '~', '@')


    /****************************************************************************/
    /**
     * Gives back the abbreviated book name from the given file.
     *
     * I have discovered recently that it is actually possible to be asked about
     * a file which does not exist -- with a Greek text like en_NETS,
     * reversification can take the whole of eg LJE (which exists in the raw
     * text) and turn it into BAR, leaving LJE no longer existing.  To cater for
     * this, I return -1 if the file does not exist.
     *
     * @param filePath
     * @return Abbreviated book name or -1 if the file does not exist.
     */

    fun getBookAbbreviationFromFile (filePath: String): String?
    {
        return try
        {
            val document = Dom.getDocument(filePath)
            var bookNode = Dom.findNodeByName(document, "book")
            if (null == bookNode) bookNode = Dom.findNodeByName(document, "_X_book")
            return Dom.getAttribute(bookNode!!, "code")
        }
        catch (_: Exception)
        {
            null
        }
    }


    /**********************************************************************************************************************/
    /**
     * Returns the UBS book number for the book represented by the given file, or -1 on error.
     *
     * @param filePath
     * @return Book number.
     */

    fun getBookNumberFromFile (filePath: String): Int
    {
        val s = getBookAbbreviationFromFile(filePath) ?: return -1
        return BibleBookNamesUsx.nameToNumber(s)
    }


    /**********************************************************************************************************************/
    /**
     * Returns either tagName or tagName plus style where the latter is defined.
     *
     * @param node: Node whose details are required.
     * @return Expanded tag name.
     */

    fun getExtendedNodeName (node: Node): String
    {
        var res = Dom.getNodeName(node)
        if (Dom.hasAttribute(node, "style")) res += ":" + Dom.getAttribute(node, "style")
        return res
    }


    /******************************************************************************************************************/
    /**
     * Creates the collection of nodes required to represent a footnote.
     *
     * @param document
     * @param refKey
     * @param text
     * @param callout If null, the default value from the configuration data is used.  If a MarkerHandler, then the
     *                next value obtained from that.
     * @return 'note' node for footnote.
     */

    fun makeFootnote (document: Document, refKey: RefKey, text: String, callout: Any? = null): Node
    {
      val caller =
        when (callout)
        {
          null      -> ConfigData.get("stepExplanationCallout")
          is String -> callout
          else      -> (callout as MarkerHandler).get()
        }

      val note = Dom.createNode(document, "<note style='f' caller='$caller'/>")

      val fr = Dom.createNode(document, "<char style='fr' closed='false'/>")
      val ref = Ref.rd(refKey)
      ref.setB(RefBase.C_DummyElement)
      fr.appendChild(Dom.createTextNode(document, ref.toString()))
      note.appendChild(fr)

      val ft = Dom.createNode(document, "<char style='ft' closed='false'/>")
      ft.appendChild(Dom.createTextNode(document, text))
      note.appendChild(ft)

      return note
    }


    /******************************************************************************************************************/
    /**
     * Changes the tagName and possibly the style of a given node, recording details of what has happened.
     *
     * @param node Node to be changed.
     * @param newTag New name for node.
     * @param newStyle New style or null.
     * @param reason Why the change has been made.
     * @return The node itself.
     */

    fun recordTagChange (node: Node, newTag: String, newStyle: String?, reason: String): Node
    {
        Dom.setAttribute(node, "_X_origTag", getExtendedNodeName(node))
        Dom.setAttribute(node, "_X_action", "Changed tagName or style")
        Dom.setAttribute(node, "_X_tagOrStyleChangedReason", reason)
        Dom.setNodeName(node, newTag)

        if (null == newStyle)
            Dom.deleteAttribute(node, "style")
        else
            Dom.setAttribute(node, "style", newStyle)

        return node
    }


    /******************************************************************************************************************/
    /**
    * Reports the fact that a particular book is being processed.
    *
    * @param document The document being handled.
    */

    fun reportBookBeingProcessed (document: Document)
    {
      val bookNode = Dom.findNodeByName(document, "_X_book") ?: Dom.findNodeByName(document, "book")!!
      val bookName = Dom.getAttribute(bookNode, "code")!!
      Dbg.reportProgress("Processing $bookName", 1)
    }


    /******************************************************************************************************************/
    /**
     * Runs a (possibly empty) list of external commands.
     *
     * There are some issues to be aware of here.  First, don't be tempted to pass a command which includes '>' to
     * redirect output to a file -- ProcessBuilder can't cope with that.  To get round it, you can use the
     * redirectFile parameter here. If you do that, however, I have at least some anecdotal evidence that not all of the
     * output ends up in the file.  (This occurred when I was sending both stderr and stdout to the same file, for which
     * reason I presently redirect stderr only, but this may not be acceptable.)
     *
     * @param prompt Written to System.out if non-null.
     * @param command Command to be run.
     * @param workingDirectory Directory to move to before running command.
     * @param errorFilePath If non-null, output is redirected to here
     * @throws StepException Any exception noticed while attempting to rub the commands.
     */

    fun runCommand (prompt: String?, command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null)
    {
      if (null != prompt) Dbg.reportProgress(prompt + java.lang.String.join(" ", command))
      val pb = ProcessBuilder(command)
      if (null != errorFilePath) pb.redirectError(File(errorFilePath))
      if (null != workingDirectory) pb.directory(File(workingDirectory))
      val process = pb.start()
      process.waitFor()
      Dbg.reportProgress("osis2mod completed")
    }


    /******************************************************************************************************************/
    private class SidifyDataStore  { var m_BookAbbrev: String = ""; var m_ChapterSid: String = "" }


    /******************************************************************************************************************/
    /**
    * Irons out chapter- and verse- identities.  This can cope either with USX2 or USX3+ format (the former identified
    * chapters and verses using a 'number' attribute; the latter uses sids).  The aim is to add sids to all chapters
    * and verses.  The routine assumes that if *anything* already has a sid, *everything* will, and therefore does
    * nothing under these circumstances.  (In this context, note that there is no point in calling this routine for
    * enhanced USX, because there it definitely won't do anything.  Note also that the routine does not do anything
    * towards generating encapsulating chapter tags -- we do use them in the enhanced USX, but this routine doesn't
    * cater for that.)
    *
    * @param document
    * @return True if anything was done.
    */

   fun sidify (document: Document): Boolean
   {
     val dataStore = SidifyDataStore()
     Dom.collectNodesInTree(document).forEach { if (!sidifyNode(it, dataStore)) return false }
     return true
   }


   /******************************************************************************************************************/
   /**
   * A version of sidify (qv) which works with just a single chapter.  It may well be useful / possible to use the
   * present method (and sidify) to replace similar code dotted through the system, if you ever find yourself at a
   * loose end.
   *
   * @param node Chapter node.
   * @param dataStore Used to record data from one call to the next.
   * @return True if anything was done.
   */

   private fun sidifyNode (node: Node, dataStore: SidifyDataStore): Boolean
   {
     /****************************************************************************************************************/
     when (Dom.getNodeName(node))
     {
       "_X_book" -> return false // Only appears in enhanced USX, when there is nothing we need do here.

       "book" ->
       {
         dataStore.m_BookAbbrev = Dom.getAttribute(node, "code")!!
       }

       "chapter" ->
       {
         if (!Dom.hasAttribute(node, "sid"))
         {
           dataStore.m_ChapterSid = dataStore.m_BookAbbrev + " " + Dom.getAttribute(node, "number")
           Dom.setAttribute(node, "sid", dataStore.m_ChapterSid)
         }
       }

       "verse" ->
       {
         if (!Dom.hasAttribute(node, "sid") && !Dom.hasAttribute(node, "eid"))
         {
           val sid = dataStore.m_ChapterSid + ":" + Dom.getAttribute(node, "number")
           Dom.setAttribute(node, "sid", sid)
         }
       }
     }



     /****************************************************************************************************************/
     return true
   }
}
