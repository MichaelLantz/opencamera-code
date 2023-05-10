# opencamera-code

This is a special edition of Open Camera using Minds DB Object Detection Integration PPE Demo into Open Camera. This includes taking a picture, uploading picture to dataset on hugging face, using that newly uploaded image with MindsDB to predict object.

[Opencamerawithcomputervision.webm](https://github.com/MichaelLantz/opencamera-code/assets/241893/18ed4977-b75e-4d9e-82b3-d2715f90bdf1)

PS: Had to add 30 second delay from time image is uploaded to the time object detection takes place. I think it's due to a lag in backend processing before image can be used for object recognition.

PSS: Future iterations will hopefully upload  image directly to Minds DB and use the the file interface instead of the string URL interface for a performance boost.
