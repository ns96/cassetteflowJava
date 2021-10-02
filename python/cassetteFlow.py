# -*- coding: utf-8 -*-
"""
A test script for reading data from audio cassette and controlling
the playback of mp3 files

Created on Wed Sep  1 15:17:39 2021

@author: Nathan Stevens
"""
import time
import os
import eyed3
import hashlib
import threading
import random
from sys import stdout
from pygame import mixer

tapeId = ''
mp3Id = ''
mp3Time = -1
totalTime = -1
mp3Filename = ''
mp3Playtime = ''
startTime = 0
trackNumber = 0
stopPlay = False

tapeList = list()
hashToMP3 = dict()
hashToMP3Time = dict()

mixer.init()

# function to track the current playtime to see if playback was stopped
def checkPlayback():
    global mp3Id, stopPlay
    
    while True:
        if stopPlay:
            mixer.music.stop()
            mp3Id = "STOP"
            stopPlay = False
            print("\nPlayback stopped ...")
            break
        
        time.sleep(0.5)
    
# function to process data
def processData(data):
    global tapeId, mp3Id, mp3Time, totalTime, mp3Filename, mp3Playtime, startTime
    
    totalTime = int(data[4]) # total count time from cassette data
    track = data[1]
    
    if tapeId != data[0]:
        tapeId = data[0]
        print("Playing Tape: " + data[0])
    
    if mp3Id != data[2]:
        if data[3] != '000M':    
            startTime = int(data[3])
            mp3Id = data[2]
            mp3Filename = hashToMP3[mp3Id]
            mp3Playtime = hashToMP3Time[mp3Id]
            mixer.music.load(mp3Filename)
            #mixer.music.set_pos(playTime) # does not work on windows 10?
            mixer.music.play(0, startTime)
            
            # start thread to track playback         
            t1 = threading.Thread(target=checkPlayback)
            t1.start()
            
            print("\nPlaying MP3:", data[2], 'Start Time: ', startTime, "Track #", track)
        else:
            print('Mute Time ...')
            return
    
    ntime = int(data[3]) # current playtime for mp3
    
    if mp3Time != ntime:
        mp3Time = ntime
        mp3Time = int(mixer.music.get_pos()/1000)
        stdout.write("\r[ " + mp3Filename + " {" + track + "} >> Tape Playtime: " + data[3] + "/" + mp3Playtime + " | MP3 Playtime: " + str(mp3Time + startTime).zfill(4) + " | Tape Counter: " + totalTime + " ]")
        stdout.flush()
    #print(data, end="\r\n")    

# function to open end of the file and continuosly read it
def follow(thefile):
    thefile.seek(0, os.SEEK_END) 
    while True:
        line = ''
        try:
            line = thefile.readline()
        except:
            print("Error reading line data ...")
                
        if not line:
            time.sleep(0.6)
            continue
        yield line

def streamCassetteData():
    global stopPlay
    '''
    Stream data from the log file generated using the minimodem
    
    "minimodem -r 1200 &>> tape.log" OR "minimodem -r 1200 &> >(tee -a tape.log)"
    
    These commands pipes both stdout stderr to a log file with the second one 
    also outputing to the screen as well
    '''    
    
    logfile = open("tape.log","r")
    datalines = follow(logfile)
    for line in datalines:
        line = line.strip()
        if len(line) == 29:
            data = line.split('_')
            processData(data)
        elif '### NOCARRIER' in line:
            stopPlay = True

def getDuration(s):
    """Module to get the convert Seconds to a time like format."""
    s = s
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    d, h = divmod(h, 24)
    timelapsed = "{:01d}:{:02d}:{:02d}:{:02d}".format(int(d),
                                                      int(h),
                                                      int(m),
                                                      int(s))
    return timelapsed

def getFilenameHash(filename):
    byteInput = filename.encode('utf-8')
    filenameHash = hashlib.md5(byteInput).hexdigest()[:10]
    return filenameHash        

# get a set of random mp3 for cassette
def getCassetteTracks(tapeLength):
    global tapeList
    
    print("\nGenerating Cassette Tracks...")
    random.shuffle(tapeList)
    tapeA = list()
    
    totalTime = 0
    tapeTime = 0
    tc = 1
    for mp3Info in tapeList:
        totalTime += mp3Info[2] + 4 # add 4 seconds of delay to each track
        
        if totalTime <= tapeLength:
            tapeA.append(mp3Info)
            print(tc, mp3Info[0], mp3Info[1], mp3Info[2])
            tc += 1
            tapeTime = totalTime
        else:
            break
    
    print(tapeLength, "Tape Length:", tapeTime, '|' , getDuration(tapeTime))
    return tapeA

def createCassetteData(tapeLength):
    myfile = open('tape1A.txt', 'w')
    timeTotal = 0;
    mp3Count = 0
    
    for mp3Info in getCassetteTracks(tapeLength):
        track_s = str(mp3Count+1).zfill(2)
        mp3Id = '0001A_' + track_s + '_' + mp3Info[1]
              
        # add line records to create a 4 second muted section before next song
        if mp3Count >= 1:
            for _ in range(4):
                timeTotal += 1
                ts_total = str(timeTotal).zfill(4)
                line = mp3Id + '_000M_' + ts_total + '\n'
                for _ in range(4): # replicate record 4 times
                    myfile.write(line)
        
        for i in range(mp3Info[2]):
            ts = str(i).zfill(4)
            ts_total = str(timeTotal).zfill(4)
            line = mp3Id + '_' + ts + '_' + ts_total + '\n'
            
            for _ in range(4): # replicate record 4 times
                myfile.write(line)
            
            timeTotal += 1
    
        mp3Count += 1        
    
    # close the file        
    myfile.close()
    
def processMP3Dir(directory):
    global tapeList, hashToMP3, hashToMP3Time
    totalTime = 0
    
    for filename in os.listdir(directory):
        if filename.endswith(".mp3"):
            fullname = os.path.join(directory, filename)
            filenameHash = getFilenameHash(filename)
            hashToMP3[filenameHash] = fullname
            
            # get mp3 meta data
            audio = eyed3.load(fullname)
            playTime = int(audio.info.time_secs)
            totalTime += playTime
            
            hashToMP3Time[filenameHash] = str(playTime).zfill(4)
            tapeList.append([filename, filenameHash, playTime, totalTime])
                
            print(filenameHash, filename, getDuration(playTime), '/' , playTime)
    
    # print out the total playtime
    print("Total PlayTime: ", int(totalTime))
  
if __name__ == '__main__':    
    processMP3Dir('C:\mp3files')
    processMP3Dir('C:\mp3files\Tape01A')
    #createCassetteData(2700) # 90 min tape
    #createCassetteData(3300) # 110 min tape
    streamCassetteData()