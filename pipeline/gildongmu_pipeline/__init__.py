"""Builds route packages for the Gildongmu road-trip host.

A route package is what the car plays back: the places along one route, each with a story in two
lengths and a few quiz questions. Nothing is generated while driving. This package gathers the
places (Wikipedia, and the Korea Tourism Organization's TourAPI when a key is available), has
Claude write the stories from that source text, checks every fact against the source, and writes
the JSON the app reads.
"""

__version__ = "0.1.0"
