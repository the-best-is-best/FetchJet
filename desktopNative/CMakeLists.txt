cmake_minimum_required(VERSION 3.21)
project(fetchjet_native LANGUAGES CXX RC)
set(CMAKE_CXX_STANDARD 20)

set(SLINT_DIR "E:/tools/slint" CACHE PATH "Unpacked Slint C++ package")
find_package(Slint REQUIRED PATHS "${SLINT_DIR}/lib/cmake/Slint")

add_executable(fetchjet_native WIN32 main.cpp res/app.rc)
target_link_libraries(fetchjet_native PRIVATE Slint::Slint)
slint_target_sources(fetchjet_native ui/app.slint)
if (MSVC)
    target_link_options(fetchjet_native PRIVATE /ENTRY:mainCRTStartup)
    target_compile_options(fetchjet_native PRIVATE /utf-8)
endif ()
add_custom_command(TARGET fetchjet_native POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${SLINT_DIR}/lib/slint_cpp.dll" $<TARGET_FILE_DIR:fetchjet_native>
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_SOURCE_DIR}/core/build/bin/mingwX64/releaseShared/fjcore.dll" $<TARGET_FILE_DIR:fetchjet_native>)
