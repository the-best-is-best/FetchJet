cmake_minimum_required(VERSION 3.21)
project(fj_shot LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)

set(SLINT_DIR "E:/tools/slint" CACHE PATH "Unpacked Slint C++ package")
find_package(Slint REQUIRED PATHS "${SLINT_DIR}/lib/cmake/Slint")

add_executable(shot shot.cpp headless.h json.h emoji_d2d.h)
target_link_libraries(shot PRIVATE Slint::Slint d2d1 dwrite windowscodecs ole32 shell32 psapi)
if (MSVC)
    target_compile_options(shot PRIVATE /utf-8 /W3)
endif ()
add_executable(deskrun deskrun.cpp)
target_link_libraries(deskrun PRIVATE user32 gdi32 psapi)
if (MSVC)
    target_compile_options(deskrun PRIVATE /utf-8 /W3)
endif ()
add_custom_command(TARGET shot POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${SLINT_DIR}/lib/slint_cpp.dll" $<TARGET_FILE_DIR:shot>)
