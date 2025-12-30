# Utility functions for printing colored messages

function(print_info MSG COLOR)
  execute_process(COMMAND printf "\\033[1;${COLOR}m${MSG}\\033[0m")
endfunction()

function(make_paths_relative out_var)
  set(result "")
  foreach(path IN LISTS ${ARGV1})
    file(RELATIVE_PATH rel "${CMAKE_CURRENT_SOURCE_DIR}" "${path}")
    list(APPEND result "${rel}")
  endforeach()
  set(${out_var} "${result}" PARENT_SCOPE)
endfunction()

function(make_preview_string LIST_VAR N)
  set(preview "")
  list(LENGTH ${LIST_VAR} list_length)
  
  foreach(item IN LISTS ${LIST_VAR})
    list(APPEND preview "${item}")
    if("${preview}" MATCHES "^.*;.*;.*$")
      break()
    endif()
  endforeach()
  
  if(list_length GREATER N)
    math(EXPR remaining_items "${list_length} - ${N}")
    list(APPEND preview "; .. .(+${remaining_items} more)")
  endif()
  
  set(PREVIOUS_SCOPE_VAR "${preview}" PARENT_SCOPE)
endfunction()
