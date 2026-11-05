// Host check for the OMT FPA1 audio frame the phone puts on the wire.
// Built and run by qa/run-audio-frame.ps1. Exit 0 means every assertion held.
#include <cassert>
#include <cstdint>
#include <cstring>
#include <vector>

#include "OMTAudioFrame.h"
#include "OMTProtocol.h"

static_assert(sizeof(omt::FrameHeader) == 16, "the OMT frame header is 16 bytes on the wire");
static_assert(sizeof(omt::AudioExtHeader) == 24, "the OMT audio extended header is 24 bytes on the wire");

static int32_t readI32(const uint8_t* p) {
    int32_t v = 0;
    std::memcpy(&v, p, sizeof(v));
    return v;
}

static int64_t readI64(const uint8_t* p) {
    int64_t v = 0;
    std::memcpy(&v, p, sizeof(v));
    return v;
}

static float readF32(const uint8_t* p) {
    float v = 0;
    std::memcpy(&v, p, sizeof(v));
    return v;
}

int main() {
    const float planar[6] = {1, 2, 3, 4, 5, 6};
    const std::vector<uint8_t> frame = omt::buildAudioFrame(planar, 2, 3, 48000, 12345);

    // 16 byte header + 24 byte audio extended header + 2 * 3 * 4 bytes of float samples.
    assert(frame.size() == 64);
    assert(frame[0] == 1);                 // version
    assert(frame[1] == 4);                 // OMTFrameType_Audio
    assert(readI64(&frame[2]) == 12345);   // timestamp in 100 ns ticks
    assert(readI32(&frame[12]) == 48);     // DataLength = 24 + payload

    assert(readI32(&frame[16]) == 0x31415046);  // FPA1
    assert(readI32(&frame[20]) == 48000);
    assert(readI32(&frame[24]) == 3);           // SamplesPerChannel
    assert(readI32(&frame[28]) == 2);           // Channels
    assert(readI32(&frame[32]) == 3);           // ActiveChannels = (1 << 2) - 1
    assert(readI32(&frame[36]) == 0);           // Reserved1

    for (int i = 0; i < 6; i++) {
        assert(readF32(&frame[40 + i * 4]) == planar[i]);
    }

    // Malformed input is refused rather than put on the wire.
    assert(omt::buildAudioFrame(planar, 0, 3, 48000, 1).empty());
    assert(omt::buildAudioFrame(planar, 33, 3, 48000, 1).empty());
    assert(omt::buildAudioFrame(planar, 2, 0, 48000, 1).empty());
    assert(omt::buildAudioFrame(planar, 2, 3, 0, 1).empty());
    assert(omt::buildAudioFrame(nullptr, 2, 3, 48000, 1).empty());

    return 0;
}