for (( i=1; i <= 40; ++i))
  do
    for (( j=1; j <= 40; ++j))
      do
        convert -size 256x256 -pointsize 72 -gravity center -border 2x2 -quality 00 label:"${i}-${j}" "${i}_${j}.png"
      done
    done
