package org.stepbible.textconverter.support.miscellaneous

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.reflect.full.createInstance


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
    * Does what it says on the tin.
    *
    * @param text Text to be copied to clipboard.
    */

    fun copyTextToClipboard (text: String)
    {
      val stringSelection = StringSelection(text)
      val clipboard = Toolkit.getDefaultToolkit().systemClipboard
      clipboard.setContents(stringSelection, null)
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
    * Creates an instance of a class given the name of the class as a string.  This assumes that there is a constructor
    * which takes no arguments.  Note that the name must be fully qualified with package details.  You can obtain the
    * package name using     val packageName = object {}.javaClass.`package`.name    within code in that package.
    *
    * @param className
    * @return Instance
    */

    fun createInstanceByClassName (className: String): Any
    {
      val klass = Class.forName(className).kotlin
      return klass.createInstance()
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

    private val C_generateRandomString_Chars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '$', '%', '_', '#', '@')


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
            return bookNode!!["code"]
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
        if ("style" in node) res += ":" + node["style"]
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
          null      -> ConfigData["stepExplanationCallout"]
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
        node["_X_origTag"] = getExtendedNodeName(node)
        node["_X_action"] = "Changed tagName or style"
        node["_X_tagOrStyleChangedReason"] = reason
        Dom.setNodeName(node, newTag)

        if (null == newStyle)
            node -= "style"
        else
            node["style"] = newStyle

        return node
    }


    /******************************************************************************************************************/
    /**
    * Runs a given function, captureing stderr and returning anything sent to stderr as a string.
    *
    * @param fn Function to run.
    * @return Anything sent to stderr.
    */

    fun capturingStderr (fn: () -> Any): String
    {
      val byteArrayOutputStream = ByteArrayOutputStream()
      val printStream = PrintStream(byteArrayOutputStream)
      val save = System.err
      System.setErr(printStream)

      fn()

      System.setErr(save)
      return byteArrayOutputStream.toString()
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
      val bookName = bookNode["code"]!!
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
      Dbg.reportProgress("External command completed")
    }


    /**************************************************************************/
    /**
    * Checks if the document uses number attributes rather than sid/eid.  If it
    * does, converts it to use sid and eid.
    *
    * This can cope with both milestone and (I think) with non-milestone.  If
    * we have a text which gives only verse- or chapter- starts, they get sids.
    * (I don't think there is any way a text in milestone form can have eids,
    * because I don't think USX caters for it.)
    *
    * @param doc Document to be processed.
    */

    fun sidify (doc: Document)
    {
      /************************************************************************/
      var bookName = ""
      var chapterNo = ""



      /************************************************************************/
      fun processNode (node: Node)
      {
        when (Dom.getNodeName(node))
        {
          /********************************************************************/
          "book", "_X_book" ->
          {
            bookName = node["code"]!!
          }



          /********************************************************************/
          "chapter", "_X_chapter" ->
          {
            if ("sid" in node) return
            if ("eid" in node) return
            chapterNo = node["number"]!!
            node -= "number"
            node["sid"] = "$bookName $chapterNo"
          }



          /********************************************************************/
          "verse" ->
          {
            if ("sid" in node) return
            if ("eid" in node) return
            val verseNo = node["number"]!!
            node -= "number"
            node["sid"] = "$bookName $chapterNo:$verseNo"
          }
        } // when
       } // processNode



       /************************************************************************/
       Dom.collectNodesInTree(doc).forEach { processNode(it) }
    } // sidify
}
