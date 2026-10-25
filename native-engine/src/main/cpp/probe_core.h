#pragma once
#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>

namespace andes {
struct InputEvent { float x; float y; std::uint64_t sequence; };
class InputQueue {
public:
    static constexpr std::size_t capacity = 128;
    bool push(float x, float y);
    bool pop(InputEvent& event);
    void pause();
    void resume();
private:
    std::mutex mutex_;
    std::deque<InputEvent> queue_;
    std::uint64_t next_ = 0;
    bool active_ = true;
};
}
