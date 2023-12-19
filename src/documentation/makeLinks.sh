#!/bin/sh -x

# This script helps with development of CSS styles for reference documentation.

cd build/site/_/css || { echo "Please generate Antora page first" && exit; }

rm jolokia.css
ln -s ../../../../supplemental-ui/css/jolokia.css .
