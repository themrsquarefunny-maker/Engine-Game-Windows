cmake_minimum_required(VERSION 3.16)
project(EngineGamePlayer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SDL3 REQUIRED)

add_library(engine_core STATIC
    src/game.cpp
    src/resources.cpp
)
target_include_directories(engine_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party
)
target_link_libraries(engine_core PUBLIC SDL3::SDL3)

add_executable(player WIN32
    src/main.cpp
)
target_link_libraries(player PRIVATE engine_core)

if(WIN32)
    target_link_options(player PRIVATE -static-libgcc -static-libstdc++)
    if(MINGW)
        target_link_options(player PRIVATE -Wl,-Bstatic -lwinpthread -Wl,-Bdynamic)
    endif()
endif()

option(BUILD_PLAYER_TESTS "Build C++ player integration tests" OFF)
if(BUILD_PLAYER_TESTS)
    add_executable(player_tests
        tests/player_tests.cpp
    )
    target_link_libraries(player_tests PRIVATE engine_core)
endif()
