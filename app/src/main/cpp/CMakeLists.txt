cmake_minimum_required(VERSION 3.22.1)

project("xiaomaotai")

add_library(
    native-secrets
    SHARED
    native-secrets.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    native-secrets
    ${log-lib}
)
