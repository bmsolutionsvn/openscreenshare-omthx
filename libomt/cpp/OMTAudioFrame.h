/**
 * OMTAudioFrame.h - build one OMT audio record (FPA1).
 *
 * Header-only and free of Android/socket includes so qa/audio-frame.cpp can check the bytes on a
 * host. The layout is libomtnet's: FrameHeader (16) + AudioExtHeader (24) + planar float payload.
 */
#ifndef OMT_AUDIO_FRAME_H
#define OMT_AUDIO_FRAME_H

#include <cstdint>
#include <cstring>
#include <vector>

#include "OMTProtocol.h"

namespace omt {

/**
 * @param planar            channels one after another, samplesPerChannel floats each
 * @param timestamp100ns    same monotonic clock as video, in 100 ns ticks
 * @return the complete record, or empty when the arguments cannot describe a frame
 */
inline std::vector<uint8_t> buildAudioFrame(const float* planar, int channels,
                                            int samplesPerChannel, int sampleRate,
                                            int64_t timestamp100ns) {
    if (planar == nullptr || channels < 1 || channels > 32 ||
        samplesPerChannel <= 0 || sampleRate <= 0) {
        return {};
    }

    const size_t payloadLength =
            static_cast<size_t>(channels) * static_cast<size_t>(samplesPerChannel) * sizeof(float);

    FrameHeader header{};
    header.version = 1;
    header.frameType = 4;   // OMTFrameType_Audio (libomt.h)
    header.timestamp = timestamp100ns;
    header.metadataLength = 0;
    header.dataLength = static_cast<int32_t>(AudioExtHeader::SIZE + payloadLength);

    AudioExtHeader ext{};
    ext.codec = Codec::FPA1;
    ext.sampleRate = sampleRate;
    ext.samplesPerChannel = samplesPerChannel;
    ext.channels = channels;
    ext.activeChannels = channels >= 32 ? 0xFFFFFFFFu : ((1u << channels) - 1u);
    ext.reserved1 = 0;

    std::vector<uint8_t> frame(sizeof(FrameHeader) + sizeof(AudioExtHeader) + payloadLength);
    std::memcpy(frame.data(), &header, sizeof(header));
    std::memcpy(frame.data() + sizeof(header), &ext, sizeof(ext));
    std::memcpy(frame.data() + sizeof(header) + sizeof(ext), planar, payloadLength);
    return frame;
}

} // namespace omt

#endif // OMT_AUDIO_FRAME_H