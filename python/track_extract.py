# -*- coding: utf-8 -*-
"""
Script to exctract track information and create a tsv file from the Kaggle 
Spotify dataset. https://www.kaggle.com/datasets/rodolfofigueroa/spotify-12m-songs 

Created on Tue Nov 29 10:06:32 2022

@author: Nathan
"""
import pandas as pd
import ast

album_set = set()

# import the csv as a dataframe
tracks = pd.read_csv('tracks_features.csv')
tracks_filtered = tracks[['name', 'album_id', 'album', 'artists', 'track_number', 'disc_number', 'duration_ms', 'year']].copy()

# create a filename
def create_filename(row):
    # store the album_id in set so we can keep track of albums
    album_set.add(row['album_id'])
    
    name = row['name'].replace("'", "").replace(",", "").replace(":", "")
    name = name.replace("(", "").replace(")", "").replace("/", "").replace(".", "")
    name = name.replace("?", "").replace("#", "").replace('"', "").replace(" ", "_")
    name = name.replace("!", "EX").replace("$", "S")
    
    if name.strip() == "":
        name = 'Blank'
        
    fn = '{:02d}'.format(row['disc_number']) + '_' + '{:02d}'.format(row['track_number']) + '_' + name
    return fn

# remove the list structure from artist
def remove_list(x):
    xlist = ast.literal_eval(x)
    return ', '.join(xlist)

# convet the list string to just a string
tracks_filtered['artists']  = tracks_filtered['artists'].apply(lambda x: remove_list(x))

# add filename column
tracks_filtered['filename'] = tracks_filtered.apply(lambda row: create_filename(row), axis=1)

# convert duration to seconds
tracks_filtered['duration'] = tracks_filtered['duration_ms'].apply(lambda x: int(x/1000))
tracks_filtered.drop('duration_ms', axis=1, inplace=True)

# print out the number of albums
print('Number of Albums: ', len(album_set))

# save as a tab delimited file tsv
tracks_filtered.to_csv('spotify_tracks.tsv', sep ='\t', index=False)