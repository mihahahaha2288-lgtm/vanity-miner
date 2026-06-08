cmake_minimum_required(VERSION 3.4.1)
project("vanity_miner")

add_library(vanity_miner SHARED vanity_miner.cpp)

target_link_libraries(vanity_miner
    android
    crypto
    ssl
    log)
