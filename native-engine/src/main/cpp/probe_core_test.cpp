#include "probe_core.h"
#include <cstdlib>
#include <iostream>
#include <limits>
#include <thread>

static void check(bool value, const char* message) {
    if (!value) { std::cerr << message << '\n'; std::exit(1); }
}
int main() {
    andes::InputQueue queue;
    andes::InputEvent event{};
    check(!queue.pop(event), "Queue must start empty");
    check(!queue.push(std::numeric_limits<float>::quiet_NaN(), 0), "Reject NaN input");
    check(queue.push(-1, 2), "Accept finite input");
    check(queue.pop(event) && event.x == 0 && event.y == 1, "Clamp coordinates");
    for (int i = 0; i < 256; ++i) queue.push(0.5f, 0.5f);
    check(queue.pop(event) && event.sequence == 129, "Overflow discards oldest events");
    queue.pause();
    check(!queue.pop(event) && !queue.push(0, 0), "Pause drains and rejects input");
    queue.resume();
    std::thread producer([&] { for (int i = 0; i < 10000; ++i) queue.push(0.2f, 0.8f); });
    std::thread consumer([&] {
        std::uint64_t last = 0;
        for (int i = 0; i < 10000; ++i) {
            andes::InputEvent value{};
            if (queue.pop(value)) { check(value.sequence > last, "Concurrent ordering"); last = value.sequence; }
        }
    });
    producer.join(); consumer.join();
    check(queue.push(0.1f, 0.9f), "Resume accepts input");
    std::cout << "PASS: bounded queue, finite coordinates, lifecycle reset, concurrent producer/consumer\n";
}
