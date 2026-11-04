-- Переразбор старых поездок после улучшения распознавания мест:
-- нужно заполнить количество мест там, где парсер раньше не справлялся,
-- и корректно разделить «свободно» и «требуется».

update log
set trip_parsed = false
where trip_time is not null
  and (trip_from is not null or trip_to is not null);
