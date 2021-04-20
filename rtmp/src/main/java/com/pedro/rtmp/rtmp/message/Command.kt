package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.amf.AmfData
import com.pedro.rtmp.amf.v0.AmfNumber
import com.pedro.rtmp.amf.v0.AmfString
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by pedro on 8/04/21.
 */
class Command(var name: String = "", var transactionId: Int = 0): RtmpMessage() {

  private val data: MutableList<AmfData> = mutableListOf()
  private var bodySize = 0

  init {
    val amfString = AmfString(name)
    data.add(amfString)
    bodySize += amfString.getSize() + 1
    val amfNumber = AmfNumber(transactionId.toDouble())
    bodySize += amfNumber.getSize() + 1
    data.add(amfNumber)
  }

  fun addData(amfData: AmfData) {
    data.add(amfData)
    bodySize += amfData.getSize() + 1
  }

  override fun readBody(input: InputStream) {
    data.clear()
    var bytesRead = 0
    while (bytesRead < header.getPacketLength()) {
      val amfData = AmfData.getAmfData(input)
      bytesRead += amfData.getSize() + 1
      data.add(amfData)
    }
    if (data.isNotEmpty()) {
      if (data[0] is AmfString) {
        name = (data[0] as AmfString).value
      }
      if (data.size >= 2 && data[1] is AmfNumber) {
        transactionId = (data[1] as AmfNumber).value.toInt()
      }
    }
  }

  override fun writeBody(output: OutputStream) {
    data.forEach {
      it.writeHeader(output)
      it.writeBody(output)
    }
  }

  override fun getSize(): Int = bodySize
}