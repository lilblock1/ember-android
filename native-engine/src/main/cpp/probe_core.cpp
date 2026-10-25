#include "probe_core.h"
#include <algorithm>
#include <cmath>

bool andes::InputQueue::push(float x, float y) {
    std::lock_guard lock(mutex_);
    if (!active_ || !std::isfinite(x) || !std::isfinite(y)) return false;
    if (queue_.size() == capacity) queue_.pop_front();
    queue_.push_back({std::clamp(x, 0.0f, 1.0f), std::clamp(y, 0.0f, 1.0f), next_++});
    return true;
}
bool andes::InputQueue::pop(InputEvent& event) {
    std::lock_guard lock(mutex_);
    if (queue_.empty()) return false;
    event = queue_.front();
    queue_.pop_front();
    return true;
}
void andes::InputQueue::pause() {
    std::lock_guard lock(mutex_);
    active_ = false;
    queue_.clear();
}
void andes::InputQueue::resume() {
    std::lock_guard lock(mutex_);
    active_ = true;
}
