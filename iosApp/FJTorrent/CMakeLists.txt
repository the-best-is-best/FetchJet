cmake_minimum_required(VERSION 3.16)
project(FJTorrent LANGUAGES C CXX OBJCXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(${LIBTORRENT_SOURCE_DIR} libtorrent)

add_library(FJTorrent STATIC FJTorrent.mm)
target_include_directories(FJTorrent PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(FJTorrent PRIVATE -fobjc-arc)
target_link_libraries(FJTorrent PUBLIC torrent-rasterbar)
