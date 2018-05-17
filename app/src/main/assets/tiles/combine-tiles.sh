for (( i=0; i <= 40; i += 2))
  do
    for (( j=0; j <= 40; j += 2))
      do
        c=$(( i + 1 ))
        r=$(( j + 1 ))
        nc=$(( c / 2 ))
        nr=$(( r / 2 ))
        echo "combine ${i}_${j} ${c}_${j} ${i}_${r} ${c}_${r} into 1/${nc}_${nr}.png"
        montage "${i}_${j}.png" "${c}_${j}.png" "${i}_${r}.png" "${c}_${r}.png" -geometry 256x256+0+0 "1/${nc}_${nr}.png"
      done
    done


#montage balloon.gif medical.gif present.gif shading.gif -geometry 256x256 new_name.png
#convert -size 256x256 -pointsize 72 -gravity center -border 2x2 -quality 00 label:"${i}-${j}" "${i}_${j}.png"
