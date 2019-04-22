include_directories(${PROJECT_SOURCE_DIR}/src)
include_directories($ENV{JAVA_HOME}/include)

set(CMAKE_CXX_FLAGS_RELEASE "-O3")

if(APPLE)
  include_directories($ENV{JAVA_HOME}/include/darwin)
elseif(UNIX)
  include_directories($ENV{JAVA_HOME}/include/linux)
  SET( CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-unused-function" )
endif()

add_library(${PROJECT_NAME} SHARED 
  danalyzer_executor_NativeCode.cpp
)

