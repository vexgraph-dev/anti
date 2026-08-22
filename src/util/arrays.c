#include "util/arrays.h"

#include <string.h>

// arrays.c — Arrays port (Legacy: util/Arrays.java). Quicksort + search over
// int32/int64 buffers, memcpy-backed block ops.

// would or should or will support other stuff, and maybe support struct based on field itself and compare...

static void swap_int(int32_t *data, size_t i, size_t j) {
    if (i == j) return;
    int32_t temp = data[i];
    data[i] = data[j];
    data[j] = temp;
}

static size_t partition_int(int32_t *data, size_t low, size_t high) {
    int32_t pivot = data[high];
    size_t i = low;
    for (size_t j = low; j < high; j++) {
        if (data[j] <= pivot) {
            swap_int(data, i, j);
            i++;
        }
    }
    swap_int(data, i, high);
    return i;
}

static void quicksort_int(int32_t *data, size_t low, size_t high) {
    if (low >= high) return;
    size_t pi = partition_int(data, low, high);
    if (pi > low) quicksort_int(data, low, pi - 1);
    if (pi < high) quicksort_int(data, pi + 1, high);
}

static void swap_long(int64_t *data, size_t i, size_t j) {
    if (i == j) return;
    int64_t temp = data[i];
    data[i] = data[j];
    data[j] = temp;
}

static size_t partition_long(int64_t *data, size_t low, size_t high) {
    int64_t pivot = data[high];
    size_t i = low;
    for (size_t j = low; j < high; j++) {
        if (data[j] <= pivot) {
            swap_long(data, i, j);
            i++;
        }
    }
    swap_long(data, i, high);
    return i;
}

static void quicksort_long(int64_t *data, size_t low, size_t high) {
    if (low >= high) return;
    size_t pi = partition_long(data, low, high);
    if (pi > low) quicksort_long(data, low, pi - 1);
    if (pi < high) quicksort_long(data, pi + 1, high);
}

void Arrays_sortInt(int32_t *data, size_t length) {
    if (!data || length <= 1) return;
    quicksort_int(data, 0, length - 1);
}

void Arrays_sortLong(int64_t *data, size_t length) {
    if (!data || length <= 1) return;
    quicksort_long(data, 0, length - 1);
}

intptr_t Arrays_binarySearchInt(const int32_t *data, size_t length, int32_t key) {
    if (!data) return -1;
    size_t low = 0;
    size_t high = length;
    while (low < high) {
        size_t mid = low + (high - low) / 2;
        int32_t mid_val = data[mid];
        if (mid_val < key)
            low = mid + 1;
        else if (mid_val > key)
            high = mid;
        else
            return (intptr_t)mid;
    }
    return -(intptr_t)(low + 1);
}

intptr_t Arrays_binarySearchLong(const int64_t *data, size_t length, int64_t key) {
    if (!data) return -1;
    size_t low = 0;
    size_t high = length;
    while (low < high) {
        size_t mid = low + (high - low) / 2;
        int64_t mid_val = data[mid];
        if (mid_val < key)
            low = mid + 1;
        else if (mid_val > key)
            high = mid;
        else
            return (intptr_t)mid;
    }
    return -(intptr_t)(low + 1);
}

void Arrays_fill(uint8_t *data, size_t length, uint8_t value) {
    if (!data || length == 0) return;
    memset(data, value, length);
}

void Arrays_copy(const uint8_t *src, uint8_t *dest, size_t bytes) {
    if (!src || !dest || bytes == 0) return;
    memcpy(dest, src, bytes);
}