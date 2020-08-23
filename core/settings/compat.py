from django.core.exceptions import EmptyResultSet
from django.db.models.sql import datastructures

datastructures.EmptyResultSet = EmptyResultSet
