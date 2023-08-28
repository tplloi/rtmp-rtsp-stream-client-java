/*
 * Copyright (C) 2023 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.srt.mpeg2ts.packets

import android.media.MediaCodec
import android.util.Log
import com.pedro.srt.mpeg2ts.AdaptationField
import com.pedro.srt.mpeg2ts.AdaptationFieldControl
import com.pedro.srt.mpeg2ts.MpegTsPacket
import com.pedro.srt.mpeg2ts.PES
import com.pedro.srt.mpeg2ts.PesType
import com.pedro.srt.mpeg2ts.psi.PSIManager
import com.pedro.srt.utils.toInt
import java.nio.ByteBuffer

/**
 * Created by pedro on 20/8/23.
 */
class AacPacket(
  sizeLimit: Int,
  psiManager: PSIManager
): BasePacket(psiManager) {

  private val header = ByteArray(7) //ADTS header
  private var sampleRate = 44100
  private var isStereo = true
  private val realSize = getRealSize(sizeLimit)

  override fun createAndSendPacket(
    byteBuffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    callback: (MpegTsPacket) -> Unit
  ) {
    if (!configSend) {
      sendConfig(realSize, callback)
      configSend = true
    }
    checkSendTable(realSize, callback)
    val length = info.size
    if (length < 0) return
    byteBuffer.rewind()
    val buffer = ByteBuffer.allocate(realSize)

    val payload = ByteArray(length + header.size)
    writeAdts(payload, length)
    byteBuffer.get(payload, header.size, length)
    var sum = 0
    var counter: Byte = 0
    val pid = psiManager.getAudioPid()
    while (sum < length) {
      val adaptationFieldControl = if (sum == 0) AdaptationFieldControl.ADAPTATION_PAYLOAD else AdaptationFieldControl.PAYLOAD
      val adaptationField = if (sum == 0) AdaptationField(
        discontinuityIndicator = false,
        randomAccessIndicator = false,
        pcr = System.nanoTime() / 1000
      ) else null
      writeTsHeader(buffer, sum == 0, pid, adaptationFieldControl, counter, adaptationField)
      val pesSize = getTSPacketSize() - getTSPacketHeaderSize() - (adaptationField?.getSize() ?: 0)
      val payloadSize = pesSize - if (sum == 0) PES.HeaderLength else 0
      val data = payload.sliceArray(sum until minOf(sum + payloadSize, payload.size))
      val pes = PES(PesType.AUDIO, payload.size, data)
      pes.write(buffer, info.presentationTimeUs, pesSize, sum == 0)
      sum += data.size
      counter = ((counter + 1) and 0xF).toByte()
    }

    val array = ByteArray(buffer.position())
    buffer.flip()
    buffer.get(array)
    callback(MpegTsPacket(array, false))
  }

  fun sendAudioInfo(sampleRate: Int, stereo: Boolean) {
    this.sampleRate = sampleRate
    this.isStereo = stereo
  }

  private fun writeAdts(buffer: ByteArray, length: Int) {
    val b = ByteBuffer.allocate(header.size)
    b.putShort((
      (0xFFF shl 4)
          or (0b000 shl 1) // MPEG-4 + Layer
          or (true.toInt())).toShort()
    )

    val samplingFrequencyIndex = AUDIO_SAMPLING_RATES.asList().indexOf(sampleRate)
    val channelConfiguration = if (isStereo) 2 else 1
    val frameLength = length + if (true) 7 else 9
    b.putInt(
      (1 shl 30) // AAC-LC = 2 - minus 1
          or (samplingFrequencyIndex shl 26)
          // 0 - Private bit
          or (channelConfiguration.toInt() shl 22)
          // 0 - originality
          // 0 - home
          // 0 - copyright id bit
          // 0 - copyright id start
          or (frameLength shl 5)
          or (0b11111) // Buffer fullness 0x7FF for variable bitrate
    )
    b.put(0b11111100.toByte()) // Buffer fullness 0x7FF for variable bitrate
    b.rewind()
    b.get(buffer, 0, header.size)
  }

  private val AUDIO_SAMPLING_RATES = intArrayOf(
    96000,  // 0
    88200,  // 1
    64000,  // 2
    48000,  // 3
    44100,  // 4
    32000,  // 5
    24000,  // 6
    22050,  // 7
    16000,  // 8
    12000,  // 9
    11025,  // 10
    8000,  // 11
    7350,  // 12
    -1,  // 13
    -1,  // 14
    -1)
}